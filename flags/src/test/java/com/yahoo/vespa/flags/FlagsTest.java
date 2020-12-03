// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.BooleanNode;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 */
public class FlagsTest {
    @Test
    public void testBoolean() {
        final boolean defaultValue = false;
        FlagSource source = mock(FlagSource.class);
        BooleanFlag booleanFlag = Flags.defineFeatureFlag("id", defaultValue, List.of("owner"), "1970-01-01", "2100-01-01", "description",
                "modification effect", FetchVector.Dimension.ZONE_ID, FetchVector.Dimension.HOSTNAME)
                .with(FetchVector.Dimension.ZONE_ID, "a-zone")
                .bindTo(source);
        assertThat(booleanFlag.id().toString(), equalTo("id"));

        when(source.fetch(eq(new FlagId("id")), any())).thenReturn(Optional.empty());
        // default value without raw flag
        assertThat(booleanFlag.value(), equalTo(defaultValue));

        ArgumentCaptor<FetchVector> vector = ArgumentCaptor.forClass(FetchVector.class);
        verify(source).fetch(any(), vector.capture());
        // hostname is set by default
        assertThat(vector.getValue().getValue(FetchVector.Dimension.HOSTNAME).isPresent(), is(true));
        assertThat(vector.getValue().getValue(FetchVector.Dimension.HOSTNAME).get(), is(not(emptyOrNullString())));
        // zone is set because it was set on the unbound flag above
        assertThat(vector.getValue().getValue(FetchVector.Dimension.ZONE_ID), is(Optional.of("a-zone")));
        // application and node type are not set
        assertThat(vector.getValue().getValue(FetchVector.Dimension.APPLICATION_ID), is(Optional.empty()));
        assertThat(vector.getValue().getValue(FetchVector.Dimension.NODE_TYPE), is(Optional.empty()));

        RawFlag rawFlag = mock(RawFlag.class);
        when(source.fetch(eq(new FlagId("id")), any())).thenReturn(Optional.of(rawFlag));
        when(rawFlag.asJsonNode()).thenReturn(BooleanNode.getTrue());

        // raw flag deserializes to true
        assertThat(booleanFlag.with(FetchVector.Dimension.APPLICATION_ID, "an-app").value(), equalTo(true));

        verify(source, times(2)).fetch(any(), vector.capture());
        // application was set on the (bound) flag.
        assertThat(vector.getValue().getValue(FetchVector.Dimension.APPLICATION_ID), is(Optional.of("an-app")));
    }

    @Test
    public void testString() {
        testGeneric(Flags.defineStringFlag("string-id", "default value", List.of("owner"), "1970-01-01", "2100-01-01", "description",
                "modification effect", FetchVector.Dimension.ZONE_ID, FetchVector.Dimension.HOSTNAME),
                "other value");
    }

    @Test
    public void testInt() {
        testGeneric(Flags.defineIntFlag("int-id", 2, List.of("owner"), "1970-01-01", "2100-01-01", "desc", "mod"), 3);
    }

    @Test
    public void testLong() {
        testGeneric(Flags.defineLongFlag("long-id", 1L, List.of("owner"), "1970-01-01", "2100-01-01", "desc", "mod"), 2L);
    }

    @Test
    public void testDouble() {
        testGeneric(Flags.defineDoubleFlag("double-id", 3.142, List.of("owner"), "1970-01-01", "2100-01-01", "desc", "mod"), 2.718);
    }

    @Test
    public void testList() {
        testGeneric(Flags.defineListFlag("list-id", List.of("a"), String.class, List.of("owner"), "1970-01-01", "2100-01-01", "desc", "mod"), List.of("a", "b", "c"));
    }

    @Test
    public void testJacksonClass() {
        ExampleJacksonClass defaultInstance = new ExampleJacksonClass();
        ExampleJacksonClass instance = new ExampleJacksonClass();
        instance.integer = -2;
        instance.string = "foo";

        testGeneric(Flags.defineJacksonFlag("jackson-id", defaultInstance, ExampleJacksonClass.class,
                List.of("owner"), "1970-01-01", "2100-01-01", "description", "modification effect", FetchVector.Dimension.HOSTNAME),
                instance);

        testGeneric(Flags.defineListFlag("jackson-list-id", List.of(defaultInstance), ExampleJacksonClass.class, List.of("owner"), "1970-01-01", "2100-01-01", "desc", "mod"),
                List.of(instance));
    }

    static <T> void testGeneric(UnboundFlag<T, ?, ?> unboundFlag, T value) {
        FlagSource source = mock(FlagSource.class);
        Flag<T, ?> flag = unboundFlag.bindTo(source);

        when(source.fetch(any(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(flag.serializer().serialize(value)));

        assertThat(flag.boxedValue(), equalTo(unboundFlag.defaultValue()));
        assertThat(flag.boxedValue(), equalTo(value));

        assertTrue(Flags.getFlag(unboundFlag.id()).isPresent());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ExampleJacksonClass {
        @JsonProperty("integer")
        public int integer = 1;

        @JsonProperty("string")
        public String string = "2";

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExampleJacksonClass that = (ExampleJacksonClass) o;
            return integer == that.integer &&
                    Objects.equals(string, that.string);
        }

        @Override
        public int hashCode() {
            return Objects.hash(integer, string);
        }
    }
}
