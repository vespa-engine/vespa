// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author baldersheim
 */
public class ChainedMapTestCase {
    @Test
    void testIsEmpty() {
        assertTrue(new ChainedMap<String, String>(Map.of(), Map.of()).isEmpty());
        assertFalse(new ChainedMap<>(Map.of("k", "v"), Map.of()).isEmpty());
        assertFalse(new ChainedMap<>(Map.of(), Map.of("k", "v")).isEmpty());
        assertFalse(new ChainedMap<>(Map.of("k", "v"), Map.of("k", "v")).isEmpty());
    }

    @Test
    void testSize() {
        assertEquals(0, new ChainedMap<String, String>(Map.of(), Map.of()).size());
        assertEquals(1, new ChainedMap<>(Map.of("k", "v"), Map.of()).size());
        assertEquals(1, new ChainedMap<>(Map.of(), Map.of("k", "v")).size());
        assertEquals(1, new ChainedMap<>(Map.of("k", "v"), Map.of("k", "v")).size());
        assertEquals(2, new ChainedMap<>(Map.of("k", "v"), Map.of("K", "v")).size());
        assertEquals(2, new ChainedMap<>(Map.of("k", "v"), Map.of("K", "v", "k", "v")).size());
    }

    @Test
    void testGetUsesBoth() {
        Map<String, String> a = Map.of("a", "a_1");
        Map<String, String> b = Map.of("b", "b_1");
        Map<String, String> ab = Map.of("a", "a_2", "b", "b_2");
        Map<String, String> a_b = new ChainedMap<>(a, b);
        assertEquals("a_1", a_b.get("a"));
        assertEquals("b_1", a_b.get("b"));

        Map<String, String> b_a = new ChainedMap<>(a, b);
        assertEquals("a_1", b_a.get("a"));
        assertEquals("b_1", b_a.get("b"));

        Map<String, String> a_ab = new ChainedMap<>(a, ab);
        assertEquals("a_1", a_ab.get("a"));
        assertEquals("b_2", a_ab.get("b"));

        Map<String, String> ab_a = new ChainedMap<>(ab, a);
        assertEquals("a_2", ab_a.get("a"));
        assertEquals("b_2", ab_a.get("b"));
    }

    @Test
    void testKeySet() {
        assertTrue(new ChainedMap<String, String>(Map.of(), Map.of()).keySet().isEmpty());
        Map<String, String> a = Map.of("a", "a_1");
        Map<String, String> b = Map.of("b", "b_1");
        Map<String, String> ab = Map.of("a", "a_2", "b", "b_2");
        assertEquals(Set.of("a"), new ChainedMap<>(a, Map.of()).keySet());
        assertEquals(Set.of("a"), new ChainedMap<>(Map.of(), a).keySet());
        assertEquals(Set.of("a", "b"), new ChainedMap<>(a, b).keySet());
        assertEquals(Set.of("a", "b"), new ChainedMap<>(ab, b).keySet());
    }
}
