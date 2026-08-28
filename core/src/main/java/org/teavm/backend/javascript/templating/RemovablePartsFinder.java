/*
 *  Copyright 2023 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
// Modified 2026 by the Brazier project (https://github.com/intisy/brazier).
package org.teavm.backend.javascript.templating;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mozilla.javascript.ast.Assignment;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.ElementGet;
import org.mozilla.javascript.ast.ExpressionStatement;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.PropertyGet;
import org.mozilla.javascript.ast.Scope;
import org.mozilla.javascript.ast.VariableDeclaration;
import org.teavm.backend.javascript.ast.AstVisitor;

public class RemovablePartsFinder extends AstVisitor {
    private Map<String, List<AstNode>> removableDeclarations = new HashMap<>();
    private Map<String, List<AstNode>> allDeclarations = new HashMap<>();
    private Set<Scope> removableDeclarationScopes = new HashSet<>();
    private Map<String, Set<String>> dependencies = new HashMap<>();
    private String insideDeclaration;
    private boolean topLevel = true;

    @Override
    public void visit(FunctionNode node) {
        if (topLevel) {
            if (node.getName() != null && !node.getName().isEmpty()) {
                record(node.getName(), node);
            }
            topLevel = false;
            insideDeclaration = node.getName();
            visit(node.getBody());
            insideDeclaration = null;
            topLevel = true;
        } else {
            super.visit(node);
        }
    }

    @Override
    public void visit(VariableDeclaration node) {
        if (topLevel) {
            for (var initializer : node.getVariables()) {
                var name = extractName(initializer.getTarget());
                if (name != null) {
                    record(name.getIdentifier(), initializer);
                    if (initializer.getInitializer() != null) {
                        topLevel = false;
                        insideDeclaration = name.getIdentifier();
                        visit(initializer.getInitializer());
                        insideDeclaration = null;
                        topLevel = true;
                    }
                }
            }
        } else {
            super.visit(node);
        }
    }

    @Override
    public void visit(ExpressionStatement node) {
        if (topLevel && node.getExpression() instanceof Assignment) {
            var assign = (Assignment) node.getExpression();
            var name = extractName(assign.getLeft());
            record(name.getIdentifier(), node.getExpression());
            if (name != null) {
                topLevel = false;
                insideDeclaration = name.getIdentifier();
                visit(assign.getRight());
                insideDeclaration = null;
                topLevel = true;
                return;
            }
        }
        super.visit(node);
    }

    @Override
    public void visit(PropertyGet node) {
        visit(node.getTarget());
    }

    @Override
    public void visit(Name node) {
        if (insideDeclaration != null) {
            var actualScope = scopeOfId(node.getIdentifier());
            if (actualScope == null || removableDeclarationScopes.contains(actualScope)) {
                dependencies.computeIfAbsent(insideDeclaration, k -> new HashSet<>()).add(node.getIdentifier());
            }
        }
    }

    private Name extractName(AstNode node) {
        if (node instanceof Name) {
            return (Name) node;
        } else if (node instanceof PropertyGet) {
            return extractName(((PropertyGet) node).getTarget());
        } else if (node instanceof ElementGet) {
            return extractName(((ElementGet) node).getTarget());
        } else {
            return null;
        }
    }

    public void markUsedDeclaration(String name) {
        removableDeclarations.remove(name);
        var dependenciesToFollow = dependencies.remove(name);
        if (dependenciesToFollow != null) {
            for (var dependency : dependenciesToFollow) {
                markUsedDeclaration(dependency);
            }
        }
    }

    public Set<AstNode> getAllRemovableParts() {
        var nodes = new HashSet<AstNode>();
        for (var parts : removableDeclarations.values()) {
            nodes.addAll(parts);
        }
        return nodes;
    }

    /**
     * {@return every top-level declaration whose name is outside {@code namesToKeep}}
     *
     * @implNote Unlike {@link #getAllRemovableParts()} this answers from the full set of
     *     declarations rather than the ones still unused, so a caller can narrow a runtime down to a
     *     chosen few names after usage marking has already run.
     */
    public Set<AstNode> getPartsExcept(Set<String> namesToKeep) {
        var nodes = new HashSet<AstNode>();
        for (var entry : allDeclarations.entrySet()) {
            if (!namesToKeep.contains(entry.getKey())) {
                nodes.addAll(entry.getValue());
            }
        }
        return nodes;
    }

    private void record(String name, AstNode node) {
        removableDeclarations.computeIfAbsent(name, k -> new ArrayList<>()).add(node);
        allDeclarations.computeIfAbsent(name, k -> new ArrayList<>()).add(node);
    }

    @Override
    protected void onEnterScope(Scope scope) {
        if (topLevel) {
            removableDeclarationScopes.add(scope);
        }
    }

    @Override
    protected void onLeaveScope(Scope scope) {
        if (topLevel) {
            removableDeclarationScopes.remove(scope);
        }
    }
}
