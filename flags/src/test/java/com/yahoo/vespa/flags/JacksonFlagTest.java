// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.Test;

import java.util.Objects;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JacksonFlagTest {
    private final FlagId id = new FlagId("id");
    private final ExampleJacksonClass defaultValue = new ExampleJacksonClass();
    private final FlagSource source = mock(FlagSource.class);
    private final JacksonFlag<ExampleJacksonClass> jacksonFlag = new JacksonFlag<>(id.toString(), ExampleJacksonClass.class, defaultValue, source);

    @Test
    public void unsetThenSet() {
        when(source.getString(id)).thenReturn(Optional.empty());
        ExampleJacksonClass value = jacksonFlag.value();
        assertEquals(1, value.integer);
        assertEquals("2", value.string);
        assertEquals("3", value.dummy);

        when(source.getString(id)).thenReturn(Optional.of("{\"integer\": 4, \"string\": \"foo\", \"stray\": 6}"));
        value = jacksonFlag.value();
        assertEquals(4, value.integer);
        assertEquals("foo", value.string);
        assertEquals("3", value.dummy);

        assertEquals(4, value.integer);
        assertEquals("foo", value.string);
        assertEquals("3", value.dummy);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ExampleJacksonClass {
        @JsonProperty("integer")
        public int integer = 1;

        @JsonProperty("string")
        public String string = "2";

        @JsonProperty("dummy")
        public String dummy = "3";

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExampleJacksonClass that = (ExampleJacksonClass) o;
            return integer == that.integer &&
                    Objects.equals(string, that.string) &&
                    Objects.equals(dummy, that.dummy);
        }

        @Override
        public int hashCode() {
            return Objects.hash(integer, string, dummy);
        }
    }
}