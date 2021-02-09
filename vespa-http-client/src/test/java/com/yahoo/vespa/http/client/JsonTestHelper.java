// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Joiner;
import org.hamcrest.MatcherAssert;

import java.io.UncheckedIOException;

import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

public class JsonTestHelper {

    private static final ObjectMapper mapper = createMapper();

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        return mapper;
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
        MatcherAssert.assertThat(inputJson, sameJSONAs(expectedJson));
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
