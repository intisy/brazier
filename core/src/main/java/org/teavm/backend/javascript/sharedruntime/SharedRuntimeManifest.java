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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a shared runtime contains, written beside it and read by every program that imports from it.
 *
 * @implNote Written and parsed by hand because the compiler has no JSON library on its runtime
 *     classpath, and adding one for four fields is not worth it. Every value is a Java class name or
 *     a generated alias, so no value can need escaping; the writer asserts that rather than
 *     assuming it.
 */
public final class SharedRuntimeManifest {
    private final String brazierVersion;
    private final List<String> classNames;
    private final List<String> aliases;

    public SharedRuntimeManifest(String brazierVersion, List<String> classNames, List<String> aliases) {
        this.brazierVersion = brazierVersion;
        List<String> sortedClasses = new ArrayList<>(classNames);
        Collections.sort(sortedClasses);
        this.classNames = Collections.unmodifiableList(sortedClasses);
        List<String> sortedAliases = new ArrayList<>(aliases);
        Collections.sort(sortedAliases);
        this.aliases = Collections.unmodifiableList(sortedAliases);
    }

    public String getBrazierVersion() {
        return brazierVersion;
    }

    public List<String> getClassNames() {
        return classNames;
    }

    public List<String> getAliases() {
        return aliases;
    }

    /**
     * {@return a digest of the alias set, which a consumer bundle records}
     *
     * @implNote Order independent, because the constructor sorts. Two bundles built against
     *     different runtimes are then distinguishable by inspection rather than by debugging a
     *     missing import at load time.
     */
    public String getAliasHash() {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
        StringBuilder joined = new StringBuilder();
        for (String alias : aliases) {
            joined.append(alias).append('\n');
        }
        byte[] digest = sha256.digest(joined.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(Character.forDigit((value >> 4) & 0xF, 16));
            hex.append(Character.forDigit(value & 0xF, 16));
        }
        return hex.toString();
    }

    /**
     * Writes the manifest.
     *
     * @param writer where to write it
     * @throws IOException if writing fails
     */
    public void write(Writer writer) throws IOException {
        writer.write("{\n");
        writer.write("  \"brazierVersion\": " + quote(brazierVersion) + ",\n");
        writer.write("  \"aliasHash\": " + quote(getAliasHash()) + ",\n");
        writeArray(writer, "classNames", classNames, true);
        writeArray(writer, "aliases", aliases, false);
        writer.write("}\n");
    }

    private static void writeArray(Writer writer, String key, List<String> values, boolean comma)
            throws IOException {
        writer.write("  \"" + key + "\": [");
        for (int i = 0; i < values.size(); ++i) {
            writer.write(i == 0 ? "\n" : ",\n");
            writer.write("    " + quote(values.get(i)));
        }
        writer.write(values.isEmpty() ? "]" : "\n  ]");
        writer.write(comma ? ",\n" : "\n");
    }

    private static String quote(String value) {
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            if (c < 0x20 || c == '"' || c == '\\') {
                throw new IllegalArgumentException("Manifest value needs escaping, which this format "
                        + "does not support: " + value);
            }
        }
        return "\"" + value + "\"";
    }

    /**
     * Reads a manifest back.
     *
     * @param reader the manifest's text
     * @return the manifest
     * @throws IOException if reading fails
     */
    public static SharedRuntimeManifest read(Reader reader) throws IOException {
        BufferedReader lines = new BufferedReader(reader);
        String version = "";
        List<String> classNames = new ArrayList<>();
        List<String> aliases = new ArrayList<>();
        List<String> current = null;
        String line;
        while ((line = lines.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.startsWith("\"brazierVersion\"")) {
                version = valueOf(trimmed);
            } else if (trimmed.startsWith("\"classNames\"")) {
                current = classNames;
            } else if (trimmed.startsWith("\"aliases\"")) {
                current = aliases;
            } else if (trimmed.startsWith("\"") && current != null) {
                current.add(stripQuotes(trimmed));
            }
        }
        return new SharedRuntimeManifest(version, classNames, aliases);
    }

    private static String valueOf(String line) {
        return stripQuotes(line.substring(line.indexOf(':') + 1).trim());
    }

    private static String stripQuotes(String text) {
        String value = text.endsWith(",") ? text.substring(0, text.length() - 1) : text;
        return value.substring(1, value.length() - 1);
    }
}
