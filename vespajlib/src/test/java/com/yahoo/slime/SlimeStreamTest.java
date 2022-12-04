// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class SlimeStreamTest {
    @Test
    public void test_empty_array() {
        var inspector = new Slime().setArray();
        var items = SlimeStream.fromArray(inspector, Inspector::asString).collect(Collectors.toList());
        assertEquals(List.of(), items);
    }

    @Test
    public void test_some_elements() {
        var inspector = new Slime().setArray();
        inspector.addString("foo");
        inspector.addString("bar");
        var items = SlimeStream.fromArray(inspector, Inspector::asString).collect(Collectors.toList());
        assertEquals(List.of("foo", "bar"), items);
    }
}
