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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.teavm.backend.javascript.sharedruntime.SharedRuntimeManifest;
import org.teavm.backend.javascript.sharedruntime.SharedRuntimeVerifier;

/**
 * Fails the build when a shipped bundle does not hold up its half of the shared-runtime bargain.
 *
 * @implNote The bundles to check are the ones a JavaScript bundler produced, not the modules the
 *     compiler emitted. Both failures this catches are introduced after compilation, by a bundler
 *     configured without the runtime in its external list, so checking the compiler's own output
 *     would report clean on a broken artifact.
 */
public abstract class CheckSharedRuntimeTask extends DefaultTask {
    /** {@return the manifest of the runtime these bundles were built against} */
    @InputFile
    public abstract RegularFileProperty getRuntimeManifest();

    /** {@return the module specifier the bundles import their runtime from} */
    @Input
    public abstract Property<String> getRuntimeModule();

    /** {@return every shipped bundle to judge} */
    @InputFiles
    public abstract ConfigurableFileCollection getBundles();

    @TaskAction
    public void check() throws IOException {
        SharedRuntimeManifest manifest;
        var manifestPath = getRuntimeManifest().get().getAsFile().toPath();
        try (var reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
            manifest = SharedRuntimeManifest.read(reader);
        }
        var module = getRuntimeModule().get();

        List<String> failures = new ArrayList<>();
        for (var bundle : getBundles()) {
            var source = new String(Files.readAllBytes(bundle.toPath()), StandardCharsets.UTF_8);
            var findings = SharedRuntimeVerifier.verify(source, module, manifest);
            var report = SharedRuntimeVerifier.report(bundle.getName(), module, findings);
            if (findings.isEmpty()) {
                getLogger().lifecycle(report);
            } else {
                failures.add(report);
            }
        }
        if (!failures.isEmpty()) {
            throw new GradleException(String.join("\n", failures));
        }
    }
}
