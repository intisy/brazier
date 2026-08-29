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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.teavm.backend.javascript.rendering.RuntimeRenderer;

/**
 * Judges a finished consumer bundle against the runtime it was built to import from.
 *
 * @implNote The subject is the bundle as it ships, after a JavaScript bundler has run over the
 *     emitted module, because that is where the two failures below are introduced and where neither
 *     the compiler nor a unit test can see them. The scan is line anchored rather than parsed: the
 *     backend emits every top-level declaration at column zero and indents every body, so a parser
 *     would buy nothing but a dependency.
 */
public final class SharedRuntimeVerifier {
    // The emitted runtime seeds its identity-hash generator and nothing else in a program does, so
    // this one symbol decides whether a bundle carries a class library or imports one.
    private static final String RUNTIME_SIGNATURE = "$rt_seed";

    // A declaration as the backend emits it: an assignment at column zero, or a function statement.
    // Anything else at column zero, such as the prototype-wiring block or the module epilogue,
    // declares nothing.
    private static final Pattern DECLARATION = Pattern.compile(
            "^(?:let |var |const )?([A-Za-z_$][\\w$]*)\\s*=(?!=)|^function ([A-Za-z_$][\\w$]*)\\s*\\(");

    // A negated class matches newlines, which is what lets one pattern span the wrapped name list
    // the backend emits for a wide import.
    private static final Pattern NAMED_IMPORT = Pattern.compile(
            "^import\\s*\\{([^}]*)\\}\\s*from\\s*\"([^\"]*)\";", Pattern.MULTILINE);

    private SharedRuntimeVerifier() {
    }

    /** What kind of mistake a {@link Finding} records. */
    public enum Problem {
        /** The bundle declares the runtime rather than importing it. */
        INLINED_RUNTIME,
        /** The bundle declares a name the runtime also exports. */
        DOUBLED_CLASS,
        /** The bundle imports a name the runtime does not export. */
        MISSING_EXPORT
    }

    /** One thing wrong with a consumer bundle. */
    public static final class Finding {
        private final Problem problem;
        private final String alias;

        Finding(Problem problem, String alias) {
            this.problem = problem;
            this.alias = alias;
        }

        /** {@return which mistake this is} */
        public Problem getProblem() {
            return problem;
        }

        /** {@return the name at issue} */
        public String getAlias() {
            return alias;
        }
    }

    /**
     * Collects the names a bundle declares at its top level.
     *
     * @param source the bundle's text
     * @return every declared name, in the order the bundle declares them
     */
    public static Set<String> scanDeclarations(String source) {
        Set<String> declared = new LinkedHashSet<>();
        for (var line : source.split("\n", -1)) {
            if (line.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                continue;
            }
            var matcher = DECLARATION.matcher(line);
            if (matcher.find()) {
                declared.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
            }
        }
        return Collections.unmodifiableSet(declared);
    }

    /**
     * Collects the names a bundle imports from one module.
     *
     * @param source the bundle's text
     * @param module the module specifier to read imports of
     * @return every name imported from that module, in the order the bundle imports them
     */
    public static Set<String> scanImports(String source, String module) {
        Set<String> imported = new LinkedHashSet<>();
        var matcher = NAMED_IMPORT.matcher(source);
        while (matcher.find()) {
            if (!matcher.group(2).equals(module)) {
                continue;
            }
            for (var name : matcher.group(1).split(",")) {
                var trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    imported.add(trimmed);
                }
            }
        }
        return Collections.unmodifiableSet(imported);
    }

    /**
     * Verifies one consumer bundle.
     *
     * @param source the bundle's text
     * @param module the module specifier the bundle imports its runtime from
     * @param manifest the runtime's manifest
     * @return everything wrong with the bundle; empty means it can load and holds one identity of
     *         every shared class
     */
    public static List<Finding> verify(String source, String module, SharedRuntimeManifest manifest) {
        var exported = new LinkedHashSet<>(manifest.getAliases());
        List<Finding> findings = new ArrayList<>();

        var declared = scanDeclarations(source);
        if (declared.contains(RUNTIME_SIGNATURE)) {
            findings.add(new Finding(Problem.INLINED_RUNTIME, RUNTIME_SIGNATURE));
        }
        for (var name : declared) {
            // The per-module names are runtime state every consumer owns a copy of, so the runtime
            // exports them and a consumer declares them, and that pair is correct rather than double.
            if (exported.contains(name) && !RuntimeRenderer.PER_MODULE_NAMES.contains(name)
                    && !name.equals(RUNTIME_SIGNATURE)) {
                findings.add(new Finding(Problem.DOUBLED_CLASS, name));
            }
        }
        for (var name : scanImports(source, module)) {
            if (!exported.contains(name)) {
                findings.add(new Finding(Problem.MISSING_EXPORT, name));
            }
        }
        return findings;
    }

    /**
     * Renders the verdict.
     *
     * @param bundleName what to call the bundle in the message
     * @param module the module specifier the bundle imports its runtime from
     * @param findings what {@link #verify} found
     * @return a one-line pass, or every problem with the fix that resolves it
     */
    public static String report(String bundleName, String module, List<Finding> findings) {
        if (findings.isEmpty()) {
            return bundleName + ": imports its runtime, declares nothing the runtime exports, and "
                    + "every imported name is exported.";
        }
        List<String> lines = new ArrayList<>();
        lines.add(bundleName + ": " + findings.size() + " problem(s) against " + module + ".");
        for (var finding : findings) {
            lines.add("  " + describe(finding, module));
        }
        return String.join("\n", lines);
    }

    private static String describe(Finding finding, String module) {
        switch (finding.getProblem()) {
            case INLINED_RUNTIME:
                return "declares " + finding.getAlias() + ", so it carries its own class library "
                        + "instead of importing one. Add \"" + module + "\" to the bundler's external list.";
            case DOUBLED_CLASS:
                return "declares " + finding.getAlias() + ", which " + module + " also exports, so one "
                        + "class has two identities and instanceof across the boundary returns false.";
            default:
                return "imports " + finding.getAlias() + ", which " + module + " does not export, so the "
                        + "bundle fails at module load.";
        }
    }
}
