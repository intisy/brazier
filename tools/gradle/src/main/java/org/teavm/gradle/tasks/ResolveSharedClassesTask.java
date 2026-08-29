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
package org.teavm.gradle.tasks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.teavm.backend.javascript.sharedruntime.SharedClassResolver;

/**
 * Writes the class list a shared runtime is built from, by resolving the alias abbreviations two or
 * more bundles were measured to share against the classes a classpath provides.
 *
 * @implNote Resolution needs the WHOLE compile classpath, not the class library alone: measured
 *     2026-08-28, classlib by itself resolved 155 of 340 abbreviations and jso, interop and platform
 *     carried the rest.
 */
public abstract class ResolveSharedClassesTask extends DefaultTask {
    /** {@return the file listing one alias abbreviation per line, as a bundle measurement reports them} */
    @InputFile
    public abstract RegularFileProperty getAbbreviationsFile();

    /** {@return every jar whose classes may back an abbreviation} */
    @InputFiles
    public abstract ConfigurableFileCollection getClasspath();

    /** {@return the file to write, one class name per line} */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void resolve() throws IOException {
        var abbreviations = Files.readAllLines(getAbbreviationsFile().get().getAsFile().toPath(),
                StandardCharsets.UTF_8);
        List<String> wanted = new ArrayList<>();
        for (var line : abbreviations) {
            var trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                wanted.add(trimmed);
            }
        }

        var result = SharedClassResolver.resolve(wanted, providedClassNames());

        // Written with an explicit newline rather than the platform's: a Windows-written list gets a
        // trailing carriage return on every class name, which then matches nothing downstream.
        var text = String.join("\n", result.getClassNames()) + "\n";
        var outputPath = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, text.getBytes(StandardCharsets.UTF_8));

        getLogger().lifecycle("Resolved {} of {} abbreviations to classes.",
                result.getClassNames().size(), wanted.size());
        if (!result.getUnresolved().isEmpty()) {
            getLogger().lifecycle("No class on the classpath abbreviates to these {}, so they are left out: {}",
                    result.getUnresolved().size(), String.join(", ", result.getUnresolved()));
        }
    }

    private List<String> providedClassNames() {
        var names = new TreeSet<String>();
        for (var file : getClasspath()) {
            if (!file.isFile() || !file.getName().endsWith(".jar")) {
                continue;
            }
            try (var jar = new ZipFile(file)) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var name = entries.nextElement().getName();
                    if (name.endsWith(".class")) {
                        names.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + file, e);
            }
        }
        return new ArrayList<>(names);
    }
}
