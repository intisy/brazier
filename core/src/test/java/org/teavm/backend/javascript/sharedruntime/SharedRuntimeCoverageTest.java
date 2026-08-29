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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.teavm.backend.javascript.codegen.DeterministicAliasProvider;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassHolder;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodHolder;
import org.teavm.model.Program;
import org.teavm.model.ValueType;

public class SharedRuntimeCoverageTest {
    private static MethodHolder method(String name, ValueType... signature) {
        var holder = new MethodHolder(name, signature);
        holder.setProgram(new Program());
        holder.setLevel(AccessLevel.PUBLIC);
        return holder;
    }

    private static ClassHolder greeter() {
        var cls = new ClassHolder("com.example.Greeter");
        cls.setLevel(AccessLevel.PUBLIC);
        cls.addMethod(method("<init>", ValueType.VOID));
        cls.addMethod(method("greet", ValueType.parse(String.class), ValueType.parse(String.class)));
        return cls;
    }

    private static Set<String> aliasesOf(ClassHolder cls) {
        var aliases = new HashSet<String>();
        aliases.add(DeterministicAliasProvider.classAliasOf(cls.getName()));
        for (var method : cls.getMethods()) {
            aliases.add(DeterministicAliasProvider.staticMethodAliasOf(method.getReference()));
            if (method.getName().equals("<init>")) {
                aliases.add(DeterministicAliasProvider.initializerAliasOf(method.getReference()));
            }
        }
        return aliases;
    }

    @Test
    public void coversAClassTheRuntimeCarriesEveryMemberOf() {
        var cls = greeter();

        assertTrue(SharedRuntimeCoverage.covers(aliasesOf(cls), cls));
        assertEquals(Collections.emptyList(), SharedRuntimeCoverage.missingAliases(aliasesOf(cls), cls));
    }

    @Test
    public void namesTheOneOverloadTheRuntimeIsMissing() {
        var cls = greeter();
        var complete = aliasesOf(cls);
        var overload = method("greet", ValueType.INTEGER, ValueType.parse(String.class));
        cls.addMethod(overload);
        var missing = DeterministicAliasProvider.staticMethodAliasOf(overload.getReference());

        assertFalse(SharedRuntimeCoverage.covers(complete, cls));
        assertEquals(Collections.singletonList(missing), SharedRuntimeCoverage.missingAliases(complete, cls));
    }

    @Test
    public void asksForNothingTheRuntimeCouldNotHaveCarried() {
        // preserveTypeWholly skips exactly these, so demanding them would report a gap that naming
        // the class in the seed list cannot close.
        var cls = greeter();
        var complete = aliasesOf(cls);

        var nativeMethod = method("wrap", ValueType.parse(Object.class));
        nativeMethod.getModifiers().add(ElementModifier.NATIVE);
        cls.addMethod(nativeMethod);

        var privateMethod = method("cache", ValueType.VOID);
        privateMethod.setLevel(AccessLevel.PRIVATE);
        cls.addMethod(privateMethod);

        var abstractMethod = new MethodHolder("describe", ValueType.parse(String.class));
        abstractMethod.getModifiers().add(ElementModifier.ABSTRACT);
        cls.addMethod(abstractMethod);

        assertTrue(SharedRuntimeCoverage.covers(complete, cls));
    }

    @Test
    public void asksForNoAllocatingWrapperOnAnAbstractClass() {
        var cls = greeter();
        cls.getModifiers().add(ElementModifier.ABSTRACT);
        var withoutWrapper = new HashSet<>(aliasesOf(cls));
        withoutWrapper.remove(DeterministicAliasProvider.initializerAliasOf(
                cls.getMethod(new MethodHolder("<init>", ValueType.VOID).getDescriptor()).getReference()));

        assertTrue(SharedRuntimeCoverage.covers(withoutWrapper, cls));
    }

    @Test
    public void treatsAStaticFieldAsPartOfTheClass() {
        var cls = greeter();
        var complete = aliasesOf(cls);
        var field = new org.teavm.model.FieldHolder("registry");
        field.getModifiers().add(ElementModifier.STATIC);
        field.setType(ValueType.parse(Object.class));
        cls.addField(field);
        var missing = DeterministicAliasProvider.staticFieldAliasOf(field.getReference());

        assertEquals(Collections.singletonList(missing), SharedRuntimeCoverage.missingAliases(complete, cls));
    }
}
