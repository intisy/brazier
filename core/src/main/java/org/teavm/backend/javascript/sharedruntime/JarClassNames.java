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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/**
 * Reads the class names a jar provides, for a build that seeds a shared runtime from whole
 * dependencies rather than from a list of class names.
 *
 * @implNote Not every {@code .class} entry names a class a lookup can resolve. A multi-release jar
 *     repeats classes under {@code META-INF/versions/<n>/}, and {@code module-info} and
 *     {@code package-info} are descriptors rather than classes, so all three are skipped: handing one
 *     to the compiler as a seed fails the build.
 */
public final class JarClassNames {
    private static final String CLASS_SUFFIX = ".class";

    private JarClassNames() {
    }

    /**
     * Reads every class a set of jars provides.
     *
     * @param files the files to read; anything that is not a jar is skipped
     * @return the class names, sorted, so a list written from them does not move between runs
     */
    public static List<String> read(Iterable<File> files) {
        var names = new TreeSet<String>();
        for (var file : files) {
            if (!file.isFile() || !file.getName().endsWith(".jar")) {
                continue;
            }
            try (var jar = new ZipFile(file)) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var name = entries.nextElement().getName();
                    if (isClassEntry(name)) {
                        names.add(name.substring(0, name.length() - CLASS_SUFFIX.length()).replace('/', '.'));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + file, e);
            }
        }
        return new ArrayList<>(names);
    }

    private static boolean isClassEntry(String name) {
        if (!name.endsWith(CLASS_SUFFIX)) {
            return false;
        }
        // A multi-release jar repeats classes under META-INF/versions/<n>/, which yields a name no
        // lookup resolves, and neither descriptor below names a class at all.
        return !name.startsWith("META-INF/")
                && !name.endsWith("/module-info" + CLASS_SUFFIX)
                && !name.equals("module-info" + CLASS_SUFFIX)
                && !name.endsWith("/package-info" + CLASS_SUFFIX);
    }
}
