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
package org.teavm.gradle.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.teavm.gradle.api.JSModuleType;
import org.teavm.gradle.api.SourceFilePolicy;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.tooling.builder.BuildStrategy;

public abstract class GenerateJavaScriptTask extends TeaVMTask {
    public GenerateJavaScriptTask() {
        getObfuscated().convention(true);
        getStrict().convention(false);
        getModuleType().convention(JSModuleType.UMD);
        getSourceMap().convention(false);
        getSourceFilePolicy().convention(SourceFilePolicy.LINK_LOCAL_FILES);
        getEntryPointName().convention("main");
        getDeterministicNames().convention(false);
    }

    @Input
    @Optional
    public abstract Property<Boolean> getObfuscated();

    @Input
    @Optional
    public abstract Property<Boolean> getStrict();

    @Input
    @Optional
    public abstract Property<JSModuleType> getModuleType();

    @Input
    @Optional
    public abstract Property<Boolean> getSourceMap();

    @Input
    @Optional
    public abstract Property<String> getEntryPointName();

    @InputFiles
    public abstract ConfigurableFileCollection getSourceFiles();

    @Input
    @Optional
    public abstract Property<SourceFilePolicy> getSourceFilePolicy();

    @Input
    @Optional
    public abstract Property<Integer> getMaxTopLevelNames();

    @Input
    @Optional
    public abstract Property<Boolean> getDeterministicNames();

    @Input
    @Optional
    public abstract Property<String> getSharedRuntimeClassesFile();

    @Input
    @Optional
    public abstract Property<String> getSharedRuntimeManifest();

    @Input
    @Optional
    public abstract Property<String> getImportedRuntimeManifest();

    @Input
    @Optional
    public abstract Property<String> getImportedRuntimeModule();

    @Override
    protected void setupBuilder(BuildStrategy builder) {
        builder.setTargetType(TeaVMTargetType.JAVASCRIPT);
        builder.setObfuscated(getObfuscated().get());
        builder.setDeterministicNames(getDeterministicNames().get());
        if (getSharedRuntimeClassesFile().isPresent()) {
            try {
                builder.setSharedRuntimeClasses(Files.readAllLines(Paths.get(getSharedRuntimeClassesFile().get())));
            } catch (IOException e) {
                throw new GradleException("Could not read the shared-runtime class list", e);
            }
        }
        if (getSharedRuntimeManifest().isPresent()) {
            builder.setSharedRuntimeManifestFile(getSharedRuntimeManifest().get());
        }
        if (getImportedRuntimeManifest().isPresent()) {
            builder.setImportedRuntimeManifestFile(getImportedRuntimeManifest().get());
        }
        if (getImportedRuntimeModule().isPresent()) {
            builder.setImportedRuntimeModule(getImportedRuntimeModule().get());
        }
        builder.setStrict(getStrict().get());
        if (getMaxTopLevelNames().isPresent()) {
            builder.setMaxTopLevelNames(getMaxTopLevelNames().get());
        }
        builder.setJsModuleType(TaskUtils.mapJsModuleType(getModuleType().get()));
        builder.setSourceMapsFileGenerated(getSourceMap().get());
        builder.setEntryPointName(getEntryPointName().get());
        TaskUtils.applySourceFiles(getSourceFiles(), builder);
        TaskUtils.applySourceFilePolicy(getSourceFilePolicy(), builder);
    }
}
