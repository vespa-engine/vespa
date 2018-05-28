// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.configsource.exports;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.Test;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class JacksonDeserializerTest {
    private static class SimpleJacksonClass {
        @JsonProperty("foo")
        public String foo;
    }

    private final JacksonDeserializer<SimpleJacksonClass> deserializer =
            new JacksonDeserializer<>(SimpleJacksonClass.class);

    @Test
    public void deserialization() {
        SimpleJacksonClass object = deserializer.deserialize(
                "{\"foo\": \"bar\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals("bar", object.foo);
    }

    @Test(expected = UncheckedIOException.class)
    public void deserializationError() {
        SimpleJacksonClass object = deserializer.deserialize(
                "{\"foo".getBytes(StandardCharsets.UTF_8));
    }
}