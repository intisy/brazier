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
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class SharedClassResolverTest {
    @Test
    public void mapsAClasslibSubstituteBackToTheClassItStandsFor() {
        assertEquals("java.util.ArrayList",
                SharedClassResolver.javaNameOf("org.teavm.classlib.java.util.TArrayList"));
        assertEquals("java.lang.String",
                SharedClassResolver.javaNameOf("org.teavm.classlib.java.lang.TString"));
    }

    @Test
    public void leavesAClassThatIsNotASubstituteAlone() {
        assertEquals("org.teavm.jso.impl.JS", SharedClassResolver.javaNameOf("org.teavm.jso.impl.JS"));
        assertEquals("org.teavm.platform.Platform", SharedClassResolver.javaNameOf("org.teavm.platform.Platform"));
    }

    @Test
    public void keepsANestedClassWhoseOuterNameCarriesThePrefix() {
        assertEquals("java.util.HashMap$Entry",
                SharedClassResolver.javaNameOf("org.teavm.classlib.java.util.THashMap$Entry"));
    }

    @Test
    public void resolvesAnAbbreviationToTheClassItAbbreviates() {
        SharedClassResolver.Result result = SharedClassResolver.resolve(
                Collections.singletonList("ju_ArrayList"),
                Arrays.asList("org.teavm.classlib.java.util.TArrayList", "org.teavm.classlib.java.lang.TString"));

        assertEquals(Collections.singletonList("java.util.ArrayList"), result.getClassNames());
        assertTrue(result.getUnresolved().isEmpty());
    }

    @Test
    public void reportsAnAbbreviationNothingProvides() {
        SharedClassResolver.Result result = SharedClassResolver.resolve(
                Arrays.asList("ju_ArrayList", "igiai_Block"),
                Collections.singletonList("org.teavm.classlib.java.util.TArrayList"));

        assertEquals(Collections.singletonList("java.util.ArrayList"), result.getClassNames());
        assertEquals(Collections.singletonList("igiai_Block"), result.getUnresolved());
    }

    @Test
    public void returnsTheClassNamesSorted() {
        SharedClassResolver.Result result = SharedClassResolver.resolve(
                Arrays.asList("ju_ArrayList", "jl_String", "ju_HashMap"),
                Arrays.asList("org.teavm.classlib.java.util.TArrayList", "org.teavm.classlib.java.lang.TString",
                        "org.teavm.classlib.java.util.THashMap"));

        assertEquals(Arrays.asList("java.lang.String", "java.util.ArrayList", "java.util.HashMap"),
                result.getClassNames());
    }

    @Test
    public void prefersTheSubstituteWhenBothItAndAPlainClassAbbreviateTheSame() {
        SharedClassResolver.Result result = SharedClassResolver.resolve(
                Collections.singletonList("ju_ArrayList"),
                Arrays.asList("java.util.ArrayList", "org.teavm.classlib.java.util.TArrayList"));

        assertEquals(Collections.singletonList("java.util.ArrayList"), result.getClassNames());
    }
}
