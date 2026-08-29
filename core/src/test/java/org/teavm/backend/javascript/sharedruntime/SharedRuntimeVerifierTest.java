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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class SharedRuntimeVerifierTest {
    private static final String MODULE = "./runtime.js";

    // Every fixture below is the shape the backend actually emits, including the wrapped import
    // whose module specifier lands on its own line.
    private static final String CLEAN_CONSUMER = ""
            + "\"use strict\";\n"
            + "import { ju_ArrayList__init__1pkqqz, jl_String_uewfaw, $rt_seed } from\n"
            + "\"./runtime.js\";\n"
            + "let $rt_stringPool_instance,\n"
            + "$rt_stringPool = strings => {\n"
            + "    $rt_stringPool_instance = new Array(strings.length);\n"
            + "},\n"
            + "$rt_s = index => $rt_stringPool_instance[index];\n"
            + "let $rt_export_main = $rt_mainStarter(p_ConsumerA_main_p13jxv);\n";

    private static SharedRuntimeManifest runtime() {
        return new SharedRuntimeManifest("0.2.0", Collections.singletonList("java.util.ArrayList"),
                Arrays.asList("ju_ArrayList", "ju_ArrayList__init__1pkqqz", "jl_String_uewfaw", "$rt_seed",
                        "$rt_stringPool", "$rt_stringPool_instance", "$rt_s"));
    }

    private static List<SharedRuntimeVerifier.Finding> verify(String source) {
        return SharedRuntimeVerifier.verify(source, MODULE, runtime());
    }

    @Test
    public void passesAConsumerThatImportsItsRuntime() {
        assertEquals(Collections.emptyList(), verify(CLEAN_CONSUMER));
    }

    @Test
    public void failsAConsumerThatDeclaredTheRuntimeSeed() {
        var findings = verify("let $rt_seed = 2463534242,\n$rt_nextId = () => {\n};\n");

        assertEquals(1, findings.size());
        assertEquals(SharedRuntimeVerifier.Problem.INLINED_RUNTIME, findings.get(0).getProblem());
        assertEquals("$rt_seed", findings.get(0).getAlias());
    }

    @Test
    public void tellsAnInlinedRuntimeApartFromAnImportedOne() {
        assertFalse(SharedRuntimeVerifier.scanDeclarations(CLEAN_CONSUMER).contains("$rt_seed"));
    }

    @Test
    public void failsAConsumerThatDeclaresAClassTheRuntimeExports() {
        var findings = verify(CLEAN_CONSUMER + "function ju_ArrayList() {\n    this.array = null;\n}\n");

        assertEquals(1, findings.size());
        assertEquals(SharedRuntimeVerifier.Problem.DOUBLED_CLASS, findings.get(0).getProblem());
        assertEquals("ju_ArrayList", findings.get(0).getAlias());
    }

    @Test
    public void acceptsThePerModuleRuntimeStateEveryConsumerOwnsACopyOf() {
        // The runtime exports these and every consumer declares them; that pair is the design
        // rather than a double, and reading it as one would fail every correct bundle.
        assertEquals(Collections.emptyList(), verify(CLEAN_CONSUMER));
        assertTrue(SharedRuntimeVerifier.scanDeclarations(CLEAN_CONSUMER).contains("$rt_stringPool"));
        assertTrue(SharedRuntimeVerifier.scanDeclarations(CLEAN_CONSUMER).contains("$rt_s"));
    }

    @Test
    public void failsAConsumerImportingAnAliasTheRuntimeDoesNotExport() {
        var findings = verify("import { ju_ArrayList__init__1pkqqz, ju_HashMap_get_abcdef } from \"./runtime.js\";\n");

        assertEquals(1, findings.size());
        assertEquals(SharedRuntimeVerifier.Problem.MISSING_EXPORT, findings.get(0).getProblem());
        assertEquals("ju_HashMap_get_abcdef", findings.get(0).getAlias());
    }

    @Test
    public void ignoresAnImportFromAModuleThatIsNotTheRuntime() {
        var findings = verify("import { somethingElse } from \"./other.js\";\n" + CLEAN_CONSUMER);

        assertEquals(Collections.emptyList(), findings);
    }

    @Test
    public void attributesNothingToAnIndentedLineOrAWiringBlock() {
        var declared = SharedRuntimeVerifier.scanDeclarations(""
                + "function ju_ArrayList() {\n"
                + "    let inner = 1;\n"
                + "}\n"
                + "{\n"
                + "}\n"
                + "if (typeof Reflect === 'object') {\n"
                + "}\n");

        assertEquals(Collections.singleton("ju_ArrayList"), declared);
    }

    @Test
    public void namesTheFixInTheMessageForEachProblem() {
        var inlined = SharedRuntimeVerifier.report("bundle.js", MODULE,
                verify("let $rt_seed = 2463534242;\n"));
        assertTrue(inlined.contains("external list"));

        var doubled = SharedRuntimeVerifier.report("bundle.js", MODULE,
                verify(CLEAN_CONSUMER + "function ju_ArrayList() {\n}\n"));
        assertTrue(doubled.contains("instanceof"));
    }
}
