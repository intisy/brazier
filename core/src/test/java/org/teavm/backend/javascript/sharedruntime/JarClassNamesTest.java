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

import static org.junit.Assert.assertEquals;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JarClassNamesTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private File jarOf(String name, String... entries) throws IOException {
        var jar = folder.newFile(name);
        try (var out = new ZipOutputStream(new FileOutputStream(jar))) {
            for (var entry : entries) {
                out.putNextEntry(new ZipEntry(entry));
                out.closeEntry();
            }
        }
        return jar;
    }

    @Test
    public void readsEveryClassAJarProvides() throws IOException {
        var jar = jarOf("seed.jar", "seed/Alpha.class", "seed/Beta.class", "seed/Beta$Inner.class");

        assertEquals(Arrays.asList("seed.Alpha", "seed.Beta", "seed.Beta$Inner"),
                JarClassNames.read(Collections.singletonList(jar)));
    }

    @Test
    public void skipsTheEntriesThatNameNoClass() throws IOException {
        var jar = jarOf("mixed.jar",
                "seed/Alpha.class",
                "seed/package-info.class",
                "module-info.class",
                "seed/module-info.class",
                "META-INF/versions/17/seed/Alpha.class",
                "META-INF/MANIFEST.MF",
                "seed/logo.png");

        assertEquals(Collections.singletonList("seed.Alpha"),
                JarClassNames.read(Collections.singletonList(jar)));
    }

    @Test
    public void mergesSeveralJarsAndSortsThem() throws IOException {
        var first = jarOf("first.jar", "b/Second.class");
        var second = jarOf("second.jar", "a/First.class", "b/Second.class");

        assertEquals(Arrays.asList("a.First", "b.Second"), JarClassNames.read(Arrays.asList(first, second)));
    }

    @Test
    public void ignoresAnythingThatIsNotAJar() throws IOException {
        var notAJar = folder.newFile("classes.zip");
        var directory = folder.newFolder("classes");

        assertEquals(Collections.emptyList(), JarClassNames.read(Arrays.asList(notAJar, directory)));
    }
}
