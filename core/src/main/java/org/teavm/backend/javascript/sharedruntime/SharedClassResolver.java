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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.teavm.backend.javascript.codegen.DefaultAliasProvider;

/**
 * Turns the class abbreviations that begin an emitted alias back into the class names they stand
 * for, so a shared runtime can be built from a measurement of what several bundles duplicate.
 *
 * @implNote The abbreviation is not reversible on its own: {@code ju_ArrayList} names a package
 *     whose segments began j and u, which is only {@code java.util} once you know what exists. So
 *     resolution is a lookup against the classes a compile classpath actually provides, and an
 *     abbreviation that matches nothing is REPORTED rather than dropped. Silence there is what makes
 *     "excluded on purpose" and "missed" indistinguishable, and a program's own classes are always
 *     in the first group.
 */
public final class SharedClassResolver {
    // The class library has two roots and only one of them substitutes: `java` mirrors the JDK's
    // packages class for class, while `impl` is support code that stands for nothing. Requiring the
    // mirrored root makes an `impl` class named TSomething safe by construction rather than by there
    // happening to be none today.
    private static final String SUBSTITUTE_PACKAGE = "org.teavm.classlib.java.";

    private SharedClassResolver() {
    }

    /** What {@link #resolve} found, and what it could not find. */
    public static final class Result {
        private final List<String> classNames;
        private final List<String> unresolved;

        Result(List<String> classNames, List<String> unresolved) {
            this.classNames = Collections.unmodifiableList(classNames);
            this.unresolved = Collections.unmodifiableList(unresolved);
        }

        /** {@return every resolved class name, sorted, so a written list does not move between runs} */
        public List<String> getClassNames() {
            return classNames;
        }

        /** {@return every abbreviation no provided class abbreviates to, in the order given} */
        public List<String> getUnresolved() {
            return unresolved;
        }
    }

    /**
     * Maps a class-library substitute back to the class it stands for.
     *
     * @param className a class name, substitute or not
     * @return the class the runtime will report, which is the substitute's subject where there is
     *         one and the name unchanged where there is not
     */
    public static String javaNameOf(String className) {
        if (!className.startsWith(SUBSTITUTE_PACKAGE)) {
            return className;
        }
        var subject = "java." + className.substring(SUBSTITUTE_PACKAGE.length());
        var lastDot = subject.lastIndexOf('.');
        if (lastDot + 1 >= subject.length() || subject.charAt(lastDot + 1) != 'T') {
            return className;
        }
        return subject.substring(0, lastDot + 1) + subject.substring(lastDot + 2);
    }

    /**
     * Resolves alias prefixes against the classes a classpath provides.
     *
     * @param prefixes the class abbreviations to resolve, as they appear at the head of an alias
     * @param providedClassNames every class name available to the build, substitutes included
     * @return the resolved class names and the abbreviations nothing provided
     */
    public static Result resolve(Collection<String> prefixes, Collection<String> providedClassNames) {
        Map<String, String> byAbbreviation = new LinkedHashMap<>();
        for (var provided : providedClassNames) {
            var javaName = javaNameOf(provided);
            // A substitute wins over a plain class of the same name: the substitute is what the
            // build actually emits, and both abbreviate identically.
            var isSubstitute = !javaName.equals(provided);
            var abbreviation = DefaultAliasProvider.suggestAliasForClass(javaName);
            if (isSubstitute || !byAbbreviation.containsKey(abbreviation)) {
                byAbbreviation.put(abbreviation, javaName);
            }
        }

        var resolved = new TreeSet<String>();
        List<String> unresolved = new ArrayList<>();
        for (var prefix : prefixes) {
            var className = byAbbreviation.get(prefix);
            if (className != null) {
                resolved.add(className);
            } else {
                unresolved.add(prefix);
            }
        }
        return new Result(new ArrayList<>(resolved), unresolved);
    }
}
