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

import java.util.Set;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.ExpressionStatement;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.VariableDeclaration;
import org.teavm.backend.javascript.ast.AstVisitor;

public class AstRemoval extends AstVisitor {
    private Set<AstNode> nodes;

    public AstRemoval(Set<AstNode> nodes) {
        this.nodes = nodes;
    }

    /**
     * @implNote Only a top-level function DECLARATION ever reaches the removal set, because that is
     *     all {@link RemovablePartsFinder} records under a function's own name, so a nested or
     *     anonymous function never matches.
     */
    @Override
    public void visit(FunctionNode node) {
        if (nodes.contains(node)) {
            replaceWith(null);
        } else {
            super.visit(node);
        }
    }

    @Override
    public void visit(ExpressionStatement node) {
        if (nodes.contains(node.getExpression())) {
            replaceWith(null);
        } else {
            super.visit(node);
        }
    }

    @Override
    public void visit(VariableDeclaration node) {
        for (var iter = node.getVariables().iterator(); iter.hasNext();) {
            var initializer = iter.next();
            if (nodes.contains(initializer)) {
                iter.remove();
            }
        }
        if (node.getVariables().isEmpty()) {
            replaceWith(null);
        }
    }
}
