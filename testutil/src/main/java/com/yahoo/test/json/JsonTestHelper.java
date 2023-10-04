// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.yahoo.test.JunitCompat;

import java.io.UncheckedIOException;

/**
 * @author Vegard Sjonfjell
 */
public class JsonTestHelper {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Returns a normalized JSON String.
     *
     * <ol>
     *     <li>A JSON string with each object's names in sorted order.</li>
     *     <li>Two JSONs are equal iff their normalized JSON strings are equal.*</li>
     *     <li>The normalized JSON is (by default) an indented multi-line string to facilitate
     *     readability and line-based diff tools.</li>
     *     <li>The normalized string does not end with a newline (\n).</li>
     * </ol>
     *
     * <p>*) No effort is done to normalize decimals and may cause false non-equality,
     * e.g. 1.2e1 is not equal to 12.  This may be fixed at a later time if needed.</p>
     */
    public static String normalize(String json) {
        JsonNode jsonNode;
        try {
            jsonNode = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON", e);
        }

        return JsonNodeFormatter.toNormalizedJson(jsonNode, false);
    }

    /**
     * Convenience method to input JSON without escaping double quotes and newlines
     * Each parameter represents a line of JSON encoded data
     * The lines are joined with newline and single quotes are replaced with double quotes
     */
    public static String inputJson(String... lines) {
        return Joiner.on("\n").join(lines).replaceAll("'", "\"");
    }

    /** Structurally compare two JSON encoded strings */
    public static void assertJsonEquals(String inputJson, String expectedJson) {
        try {
            JsonNode expected = mapper.readTree(expectedJson);
            JsonNode actual = mapper.readTree(inputJson);
            JunitCompat.assertEquals(expected, actual);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Exception when comparing json strings." , e);
        }
    }

    /** Structurally compare a {@link JsonNode} and a JSON string. */
    public static void assertJsonEquals(JsonNode left, String rightJson) {
        try {
            String leftJson = mapper.writeValueAsString(left);
            assertJsonEquals(leftJson, rightJson);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Structurally compare two {@link JsonNode}s. */
    public static void assertJsonEquals(JsonNode left, JsonNode right) {
        try {
            String leftJson = mapper.writeValueAsString(left);
            String rightJson = mapper.writeValueAsString(right);
            assertJsonEquals(leftJson, rightJson);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

}
