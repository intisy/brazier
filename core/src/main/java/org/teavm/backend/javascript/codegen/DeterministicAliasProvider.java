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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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
    private final Set<String> declaredTopLevel = new LinkedHashSet<>();
    private final Set<String> claimedLiterals = new LinkedHashSet<>();

    public DeterministicAliasProvider(int maxTopLevelNames) {
        this.maxTopLevelNames = maxTopLevelNames;
    }

    @Override
    public ScopedName getClassAlias(String className) {
        return topLevel(DefaultAliasProvider.suggestAliasForClass(className), classIdentity(className));
    }

    @Override
    public ScopedName getStaticMethodAlias(MethodReference method) {
        return topLevel(staticMethodPrefix(method), staticMethodIdentity(method));
    }

    @Override
    public ScopedName getInitializerAlias(MethodReference method) {
        return topLevel(staticMethodPrefix(method), initializerIdentity(method));
    }

    @Override
    public ScopedName getStaticFieldAlias(FieldReference field) {
        return topLevel(staticFieldPrefix(field), staticFieldIdentity(field));
    }

    @Override
    public ScopedName getClassInitAlias(String className) {
        return topLevel(classInitPrefix(className), classInitIdentity(className));
    }

    /**
     * {@return the alias a class receives, without claiming it}
     *
     * @param className the class
     */
    public static String classAliasOf(String className) {
        return aliasOf(DefaultAliasProvider.suggestAliasForClass(className), classIdentity(className));
    }

    /**
     * {@return the alias a method body receives, without claiming it}
     *
     * @param method the method
     */
    public static String staticMethodAliasOf(MethodReference method) {
        return aliasOf(staticMethodPrefix(method), staticMethodIdentity(method));
    }

    /**
     * {@return the alias a constructor's allocating wrapper receives, without claiming it}
     *
     * @param method the constructor
     */
    public static String initializerAliasOf(MethodReference method) {
        return aliasOf(staticMethodPrefix(method), initializerIdentity(method));
    }

    /**
     * {@return the alias a static field receives, without claiming it}
     *
     * @param field the field
     */
    public static String staticFieldAliasOf(FieldReference field) {
        return aliasOf(staticFieldPrefix(field), staticFieldIdentity(field));
    }

    /**
     * {@return the alias a class initializer guard receives, without claiming it}
     *
     * @param className the class
     */
    public static String classInitAliasOf(String className) {
        return aliasOf(classInitPrefix(className), classInitIdentity(className));
    }

    private static String classIdentity(String className) {
        return "class:" + className;
    }

    private static String staticMethodIdentity(MethodReference method) {
        return "staticMethod:" + method;
    }

    private static String initializerIdentity(MethodReference method) {
        return "initializer:" + method;
    }

    private static String staticFieldIdentity(FieldReference field) {
        return "staticField:" + field;
    }

    private static String classInitIdentity(String className) {
        return "clinit:" + className;
    }

    private static String staticFieldPrefix(FieldReference field) {
        return DefaultAliasProvider.suggestAliasForClass(field.getClassName()) + "_" + field.getFieldName();
    }

    private static String classInitPrefix(String className) {
        return DefaultAliasProvider.suggestAliasForClass(className) + "_$callClinit";
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
        var alias = claim(topLevelOwners, prefix, owner);
        declaredTopLevel.add(alias);
        return new ScopedName(alias, false);
    }

    /**
     * {@return every name claimed verbatim, in the order they were claimed}
     *
     * @implNote These are the {@code $rt_*} entry points and the markers a plugin emits beside them.
     *     A consumer references them exactly as the runtime declares them, so it can only tell
     *     whether to import one by asking what it claimed.
     */
    public Set<String> getClaimedLiterals() {
        return Collections.unmodifiableSet(claimedLiterals);
    }

    /**
     * {@return every top-level alias derived from a member, in the order they were minted}
     *
     * @implNote Names claimed verbatim are excluded. A reserved global or a runtime function is not
     *     something this module declares, so exporting it would export what it does not own.
     */
    public Set<String> getDeclaredTopLevelAliases() {
        return Collections.unmodifiableSet(declaredTopLevel);
    }

    private String instance(String prefix, String owner) {
        return claim(instanceOwners, prefix, owner);
    }

    private String claimLiteral(String name) {
        claimedLiterals.add(name);
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
        var alias = owner != null ? aliasOf(prefix, owner) : prefix;
        var identity = owner != null ? owner : "literal:" + prefix;
        var previous = owners.putIfAbsent(alias, identity);
        if (previous != null && !previous.equals(identity)) {
            throw new IllegalStateException("Alias '" + alias + "' is claimed by both '" + previous + "' and '"
                    + identity + "'. Deterministic naming must not merge two members.");
        }
        return alias;
    }

    private static String aliasOf(String prefix, String identity) {
        return DefaultAliasProvider.sanitize(prefix) + "_" + digest(identity);
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
