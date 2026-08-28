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
package org.teavm.backend.javascript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.teavm.vm.TeaVMOptimizationLevel;

public class SharedRuntimeModeTest {
    @Test
    public void refusesToEmitASharedRuntimeAboveSimple() {
        JavaScriptTarget target = new JavaScriptTarget();
        target.setSharedRuntimeClasses(Arrays.asList("java.lang.Object"));
        try {
            target.checkSharedRuntimeOptimizationLevel(TeaVMOptimizationLevel.ADVANCED);
            fail("expected a rejection");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("SIMPLE"));
        }
    }

    @Test
    public void allowsSimple() {
        JavaScriptTarget target = new JavaScriptTarget();
        target.setSharedRuntimeClasses(Arrays.asList("java.lang.Object"));
        target.checkSharedRuntimeOptimizationLevel(TeaVMOptimizationLevel.SIMPLE);
    }

    @Test
    public void leavesAnOrdinaryBuildAlone() {
        JavaScriptTarget target = new JavaScriptTarget();
        target.checkSharedRuntimeOptimizationLevel(TeaVMOptimizationLevel.FULL);
    }

    @Test
    public void remembersTheClassesItWasGiven() {
        JavaScriptTarget target = new JavaScriptTarget();
        target.setSharedRuntimeClasses(Arrays.asList("java.lang.Object", "java.lang.String"));
        assertEquals(2, target.getSharedRuntimeClasses().size());
        target.setSharedRuntimeClasses(Collections.<String>emptyList());
        assertEquals(0, target.getSharedRuntimeClasses().size());
    }
}
