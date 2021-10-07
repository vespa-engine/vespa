// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.reflection;

import org.junit.Test;

import java.util.Optional;

import static com.yahoo.text.StringUtilities.quote;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static com.yahoo.reflection.Casting.cast;

public class CastingTest {
    @Test
    public void valid_cast_gives_present_optional() {
        Object objectToCast = 12;
        Optional<Integer> value = cast(Integer.class, objectToCast);
        assertTrue("Value is not present", value.isPresent());
        assertThat(value.get(), is(objectToCast));
    }

    @Test
    public void invalid_cast_gives_empty_optional() {
        Object objectToCast = "string";
        Optional<Integer> value = cast(Integer.class, objectToCast);
        assertTrue("Value is present", !value.isPresent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void cast_sample_usage() {
        Object objectToCast = "illegal";
        int result = cast(Integer.class, objectToCast).
                filter(i -> !i.equals(0)).
                orElseThrow(() -> new IllegalArgumentException("Expected non-zero integer, got " + quote(objectToCast)));
    }
}
