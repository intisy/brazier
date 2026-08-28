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
package org.teavm.backend.javascript.rendering;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RuntimeRendererTest {
    @Test
    public void everyPerModuleNameIsDeclaredByARuntimePart() {
        RuntimeRenderer renderer = new RuntimeRenderer(name -> null, null, null);
        renderer.prepareAstParts(true);

        List<String> missing = new ArrayList<>();
        for (String name : RuntimeRenderer.PER_MODULE_NAMES) {
            if (!renderer.getTopLevelNames().contains(name)) {
                missing.add(name);
            }
        }

        assertTrue("per-module names no runtime part declares: " + missing, missing.isEmpty());
    }

    @Test
    public void theSimpleThreadRuntimeDeclaresThemToo() {
        RuntimeRenderer renderer = new RuntimeRenderer(name -> null, null, null);
        renderer.prepareAstParts(false);

        assertTrue(renderer.getTopLevelNames().containsAll(RuntimeRenderer.PER_MODULE_NAMES));
    }

    @Test
    public void theSharedStateThatMustStaySharedIsNotPerModule() {
        for (String name : new String[] {"$rt_seed", "$rt_meta", "$rt_javaExceptionProp", "JavaError",
                "$rt_intern", "$rt_allClasses", "$rt_currentNativeThread", "$rt_putStdout"}) {
            assertFalse(name + " must be shared across modules",
                    RuntimeRenderer.PER_MODULE_NAMES.contains(name));
        }
    }
}
