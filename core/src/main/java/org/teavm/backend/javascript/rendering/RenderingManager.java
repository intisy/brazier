/*
 *  Copyright 2016 Alexey Andreev.
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
package org.teavm.backend.javascript.rendering;

import java.util.Properties;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.common.ServiceRepository;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReference;

public interface RenderingManager extends ServiceRepository {
    SourceWriter getWriter();

    void exportMethod(MethodReference method, String alias);

    void exportClass(String className, String alias);

    void exportFunction(String functionName, String alias);

    ListableClassReaderSource getClassSource();

    ClassReaderSource getOriginalClassSource();

    ClassLoader getClassLoader();

    Properties getProperties();

    String getEntryPoint();

    /**
     * {@return whether an imported shared runtime already carries this name}
     *
     * @param alias a name this module would otherwise declare
     * @implNote False by default, so a backend that knows nothing of shared runtimes declares
     *     everything it emits, as it always has. A plugin emitting module-wide state under a fixed
     *     name asks this before declaring it, because two copies of such state are two answers to
     *     one question.
     */
    default boolean isCarriedByImportedRuntime(String alias) {
        return false;
    }
}
