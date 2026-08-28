/*
 *  Copyright 2026 the Brazier project.
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
package org.teavm.backend.javascript.sharedruntime;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.teavm.backend.javascript.codegen.NamingStrategy;
import org.teavm.backend.javascript.codegen.SourceWriterSink;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;

/**
 * Collects the names a rendered module declares at its top level, by replaying what was rendered.
 *
 * @implNote A shared runtime cannot export the names its naming strategy MINTED. A name is minted
 *     for anything referenced, and the backend elides some bodies it has minted a name for, so an
 *     export list built from minted names names things the module does not define and the module
 *     fails to load. Only what was written can be exported.
 */
public final class DeclarationCollector implements SourceWriterSink {
    private final NamingStrategy naming;
    private final Set<String> declared = new LinkedHashSet<>();
    private boolean inDeclaration;

    public DeclarationCollector(NamingStrategy naming) {
        this.naming = naming;
    }

    /** {@return every top-level name the replayed source declares} */
    public Set<String> getDeclared() {
        return Collections.unmodifiableSet(declared);
    }

    @Override
    public SourceWriterSink startVariableDeclaration() {
        inDeclaration = true;
        return this;
    }

    @Override
    public SourceWriterSink startFunctionDeclaration() {
        inDeclaration = true;
        return this;
    }

    @Override
    public SourceWriterSink declareVariable() {
        inDeclaration = true;
        return this;
    }

    @Override
    public SourceWriterSink endDeclaration() {
        inDeclaration = false;
        return this;
    }

    @Override
    public SourceWriterSink appendFunction(String name) {
        return record(name);
    }

    @Override
    public SourceWriterSink appendClass(String cls) {
        return record(naming.className(cls).name);
    }

    @Override
    public SourceWriterSink appendMethod(MethodReference method) {
        return record(naming.methodName(method).name);
    }

    @Override
    public SourceWriterSink appendStaticField(FieldReference field) {
        return record(naming.fieldName(field).name);
    }

    @Override
    public SourceWriterSink appendClassInit(String className) {
        return record(naming.classInitializerName(className).name);
    }

    @Override
    public SourceWriterSink appendInit(MethodReference method) {
        return record(naming.initializerName(method).name);
    }

    private SourceWriterSink record(String name) {
        if (inDeclaration) {
            declared.add(name);
            inDeclaration = false;
        }
        return this;
    }
}
