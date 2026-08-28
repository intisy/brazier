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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class SharedRuntimeManifestTest {
    @Test
    public void roundTripsThroughItsOwnFormat() throws IOException {
        SharedRuntimeManifest written = new SharedRuntimeManifest("0.2.0-SNAPSHOT",
                Arrays.asList("java.lang.Object", "java.lang.String"),
                Arrays.asList("jl_Object_rcekyo", "jl_String_uewfaw"));
        StringWriter out = new StringWriter();
        written.write(out);

        SharedRuntimeManifest read = SharedRuntimeManifest.read(new StringReader(out.toString()));

        assertEquals("0.2.0-SNAPSHOT", read.getBrazierVersion());
        assertEquals(written.getClassNames(), read.getClassNames());
        assertEquals(written.getAliases(), read.getAliases());
        assertEquals(written.getAliasHash(), read.getAliasHash());
    }

    @Test
    public void readsAnEmptyManifest() throws IOException {
        SharedRuntimeManifest written = new SharedRuntimeManifest("1",
                Collections.<String>emptyList(), Collections.<String>emptyList());
        StringWriter out = new StringWriter();
        written.write(out);

        SharedRuntimeManifest read = SharedRuntimeManifest.read(new StringReader(out.toString()));

        assertEquals(Collections.<String>emptyList(), read.getClassNames());
        assertEquals(Collections.<String>emptyList(), read.getAliases());
    }

    @Test
    public void hashesTheAliasesRatherThanTheirOrder() {
        SharedRuntimeManifest one = new SharedRuntimeManifest("1", Collections.<String>emptyList(),
                Arrays.asList("b", "a"));
        SharedRuntimeManifest other = new SharedRuntimeManifest("1", Collections.<String>emptyList(),
                Arrays.asList("a", "b"));
        assertEquals(one.getAliasHash(), other.getAliasHash());
    }

    @Test
    public void distinguishesADifferentAliasSet() {
        SharedRuntimeManifest one = new SharedRuntimeManifest("1", Collections.<String>emptyList(),
                Arrays.asList("a", "b"));
        SharedRuntimeManifest other = new SharedRuntimeManifest("1", Collections.<String>emptyList(),
                Arrays.asList("a", "c"));
        assertNotEquals(one.getAliasHash(), other.getAliasHash());
    }

    @Test
    public void refusesAValueThatWouldNeedEscaping() throws IOException {
        try {
            new SharedRuntimeManifest("1", Collections.singletonList("a\"b"),
                    Collections.<String>emptyList()).write(new StringWriter());
            fail("expected a rejection");
        } catch (IllegalArgumentException e) {
            assertEquals(true, e.getMessage().contains("escaping"));
        }
    }
}
