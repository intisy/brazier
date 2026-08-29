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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.teavm.backend.javascript.codegen.DeterministicAliasProvider;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodReader;

/**
 * Decides whether a shared runtime carries the whole of a class, so a consumer may skip emitting it.
 *
 * @implNote Naming a class in the manifest is not enough. A runtime is dead-code eliminated like any
 *     other program, so it emits only the members its own entry point reaches, and a consumer that
 *     reaches a further overload of a class the manifest names would emit no body for it and import
 *     none either, leaving a call to a name that exists nowhere. The check is therefore per member
 *     and the fallback is per class: emitting the class whole keeps its virtual methods on one
 *     prototype, which a partial emission could not, since the metadata that wires them belongs to
 *     whichever module declares the class.
 */
public final class SharedRuntimeCoverage {
    private static final String UNMANAGED = "org.teavm.interop.Unmanaged";

    private SharedRuntimeCoverage() {
    }

    /**
     * {@return whether every top-level name this class contributes is one the runtime exports}
     *
     * @param exportedAliases the runtime's alias set
     * @param cls the class as this program reaches it, after dead-code elimination
     */
    public static boolean covers(Set<String> exportedAliases, ClassReader cls) {
        return missingAliases(exportedAliases, cls).isEmpty();
    }

    /**
     * {@return the names this class contributes that the runtime does not export, sorted}
     *
     * @param exportedAliases the runtime's alias set
     * @param cls the class as this program reaches it, after dead-code elimination
     */
    public static List<String> missingAliases(Set<String> exportedAliases, ClassReader cls) {
        var missing = new ArrayList<String>();
        check(missing, exportedAliases, DeterministicAliasProvider.classAliasOf(cls.getName()));
        for (var field : cls.getFields()) {
            if (field.hasModifier(ElementModifier.STATIC)) {
                check(missing, exportedAliases,
                        DeterministicAliasProvider.staticFieldAliasOf(field.getReference()));
            }
        }
        for (var method : cls.getMethods()) {
            checkMethod(missing, exportedAliases, cls, method);
        }
        Collections.sort(missing);
        return missing;
    }

    private static void checkMethod(List<String> missing, Set<String> exportedAliases, ClassReader cls,
            MethodReader method) {
        if (!isPreservable(method)) {
            return;
        }
        var reference = method.getReference();
        if (method.getName().equals("<clinit>")) {
            check(missing, exportedAliases, DeterministicAliasProvider.classInitAliasOf(reference.getClassName()));
        }
        // An abstract class is never allocated, so no module generates the wrapper that would do it.
        if (method.getName().equals("<init>") && !cls.hasModifier(ElementModifier.ABSTRACT)) {
            check(missing, exportedAliases, DeterministicAliasProvider.initializerAliasOf(reference));
        }
        check(missing, exportedAliases, DeterministicAliasProvider.staticMethodAliasOf(reference));
    }

    /**
     * {@return whether preserving a class would have emitted this method}
     *
     * @param method a method of a class the runtime names
     * @implNote Mirrors {@code TeaVM.isReachableByAConsumer}, which is what decided the runtime's
     *     contents. Asking for more than the runtime could ever have carried would report a gap that
     *     no seed can close: a native method is supplied by a generator in whichever module reaches
     *     it, and a private one cannot be called from outside its class at all.
     */
    private static boolean isPreservable(MethodReader method) {
        return method.getProgram() != null
                && !method.hasModifier(ElementModifier.NATIVE)
                && method.getLevel() != AccessLevel.PRIVATE
                && method.getAnnotations().get(UNMANAGED) == null;
    }

    private static void check(List<String> missing, Set<String> exportedAliases, String alias) {
        if (!exportedAliases.contains(alias)) {
            missing.add(alias);
        }
    }
}
