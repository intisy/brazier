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
package org.teavm.backend.javascript.codegen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

/**
 * Derives every alias from the member's identity alone, so a member reached by two independently
 * compiled programs receives the same name in both and their output can share one runtime.
 *
 * <p>{@link DefaultAliasProvider} cannot offer that: it appends a counter minted in whole-program
 * encounter order, so which overload of {@code insert} becomes {@code insert2} depends on what else
 * the program happened to reach.
 *
 * @implNote The disambiguating digest is always present rather than added on collision. Whether a
 *     name collides is a property of the whole program, so a collision-triggered suffix would
 *     reintroduce exactly the order-dependence this class exists to remove.
 */
public class DeterministicAliasProvider implements AliasProvider {
    private static final int DIGEST_LENGTH = 6;
    private static final String DIGEST_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final String ADDITIONAL_SCOPE_NAME = "$rt_java";

    private final int maxTopLevelNames;
    private final Map<String, String> topLevelOwners = new HashMap<>();
    private final Map<String, String> instanceOwners = new HashMap<>();

    public DeterministicAliasProvider(int maxTopLevelNames) {
        this.maxTopLevelNames = maxTopLevelNames;
    }

    @Override
    public ScopedName getClassAlias(String className) {
        return topLevel(DefaultAliasProvider.suggestAliasForClass(className), "class:" + className);
    }

    @Override
    public ScopedName getStaticMethodAlias(MethodReference method) {
        return topLevel(staticMethodPrefix(method), "staticMethod:" + method);
    }

    @Override
    public ScopedName getInitializerAlias(MethodReference method) {
        return topLevel(staticMethodPrefix(method), "initializer:" + method);
    }

    @Override
    public ScopedName getStaticFieldAlias(FieldReference field) {
        var prefix = DefaultAliasProvider.suggestAliasForClass(field.getClassName()) + "_" + field.getFieldName();
        return topLevel(prefix, "staticField:" + field);
    }

    @Override
    public ScopedName getClassInitAlias(String className) {
        var prefix = DefaultAliasProvider.suggestAliasForClass(className) + "_$callClinit";
        return topLevel(prefix, "clinit:" + className);
    }

    @Override
    public String getMethodAlias(MethodDescriptor method) {
        return instance(instanceMethodPrefix(method.getName()), "method:" + method);
    }

    @Override
    public String getFieldAlias(FieldReference field) {
        return instance("$" + field.getFieldName(), "field:" + field);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Returned unchanged. These are the {@code $rt_*} runtime entry points, which
     *     templates and hand-written JavaScript reference literally.
     */
    @Override
    public ScopedName getFunctionAlias(String name) {
        return new ScopedName(claimLiteral(name), false);
    }

    @Override
    public String getAdditionalScopeName() {
        return claimLiteral(ADDITIONAL_SCOPE_NAME);
    }

    @Override
    public void reserveName(String name) {
        claimLiteral(name);
    }

    private static String staticMethodPrefix(MethodReference method) {
        String name;
        switch (method.getDescriptor().getName()) {
            case "<init>":
                name = "_init_";
                break;
            case "<clinit>":
                name = "_clinit_";
                break;
            default:
                name = method.getDescriptor().getName();
                break;
        }
        return DefaultAliasProvider.suggestAliasForClass(method.getClassName()) + "_" + name;
    }

    private static String instanceMethodPrefix(String methodName) {
        switch (methodName) {
            case "<init>":
                return "$_init_";
            case "<clinit>":
                return "$_clinit_";
            default:
                return "$" + methodName;
        }
    }

    private ScopedName topLevel(String prefix, String owner) {
        if (topLevelOwners.size() >= maxTopLevelNames) {
            throw new IllegalStateException("Deterministic naming reached the top-level name limit of "
                    + maxTopLevelNames + ". Scope splitting is unavailable here, because the point at which it "
                    + "starts depends on how many names were minted before it.");
        }
        return new ScopedName(claim(topLevelOwners, prefix, owner), false);
    }

    private String instance(String prefix, String owner) {
        return claim(instanceOwners, prefix, owner);
    }

    private String claimLiteral(String name) {
        return claim(topLevelOwners, name, null);
    }

    /**
     * Records that {@code owner} holds the alias derived from {@code prefix}, failing the build if
     * another identity already holds it.
     *
     * @param owners the namespace to claim in
     * @param prefix the readable part of the alias
     * @param owner the identity the alias is derived from, or null for a name used verbatim
     * @return the alias
     * @implNote A silent clash in the instance namespace would land a consumer's override on one
     *     prototype slot while its callers invoke another, so nothing fails to link and the wrong
     *     method runs. Loud is the only acceptable failure.
     */
    private static String claim(Map<String, String> owners, String prefix, String owner) {
        var alias = owner != null
                ? DefaultAliasProvider.sanitize(prefix) + "_" + digest(owner)
                : prefix;
        var identity = owner != null ? owner : "literal:" + prefix;
        var previous = owners.putIfAbsent(alias, identity);
        if (previous != null && !previous.equals(identity)) {
            throw new IllegalStateException("Alias '" + alias + "' is claimed by both '" + previous + "' and '"
                    + identity + "'. Deterministic naming must not merge two members.");
        }
        return alias;
    }

    private static String digest(String identity) {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
        var bytes = sha256.digest(identity.getBytes(StandardCharsets.UTF_8));
        var value = 0L;
        for (var i = 0; i < 8; ++i) {
            value = value << 8 | bytes[i] & 0xFFL;
        }
        var sb = new StringBuilder(DIGEST_LENGTH);
        for (var i = 0; i < DIGEST_LENGTH; ++i) {
            sb.append(DIGEST_ALPHABET.charAt((int) Long.remainderUnsigned(value, DIGEST_ALPHABET.length())));
            value = Long.divideUnsigned(value, DIGEST_ALPHABET.length());
        }
        return sb.toString();
    }
}
