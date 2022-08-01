// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.handler;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 * @author Einar M R Rosenvinge
 */
public class HeadersTestCase {

    @Test
    void requireThatSizeWorksAsExpected() {
        Headers headers = new Headers();
        assertEquals(0, headers.size());
        headers.add("foo", "bar");
        assertEquals(1, headers.size());
        headers.add("foo", "baz");
        assertEquals(1, headers.size());
        headers.add("bar", "baz");
        assertEquals(2, headers.size());
        headers.remove("foo");
        assertEquals(1, headers.size());
        headers.remove("bar");
        assertEquals(0, headers.size());
    }

    @Test
    void requireThatIsEmptyWorksAsExpected() {
        Headers headers = new Headers();
        assertTrue(headers.isEmpty());
        headers.add("foo", "bar");
        assertFalse(headers.isEmpty());
        headers.remove("foo");
        assertTrue(headers.isEmpty());
    }

    @Test
    void requireThatContainsKeyWorksAsExpected() {
        Headers headers = new Headers();
        assertFalse(headers.containsKey("foo"));
        assertFalse(headers.containsKey("FOO"));
        headers.add("foo", "bar");
        assertTrue(headers.containsKey("foo"));
        assertTrue(headers.containsKey("FOO"));
    }

    @Test
    void requireThatContainsValueWorksAsExpected() {
        Headers headers = new Headers();
        assertFalse(headers.containsValue(Arrays.asList("bar")));
        headers.add("foo", "bar");
        assertTrue(headers.containsValue(Arrays.asList("bar")));
    }

    @Test
    void requireThatContainsWorksAsExpected() {
        Headers headers = new Headers();
        assertFalse(headers.contains("foo", "bar"));
        assertFalse(headers.contains("FOO", "bar"));
        assertFalse(headers.contains("foo", "BAR"));
        assertFalse(headers.contains("FOO", "BAR"));
        headers.add("foo", "bar");
        assertTrue(headers.contains("foo", "bar"));
        assertTrue(headers.contains("FOO", "bar"));
        assertFalse(headers.contains("foo", "BAR"));
        assertFalse(headers.contains("FOO", "BAR"));
    }

    @Test
    void requireThatContainsIgnoreCaseWorksAsExpected() {
        Headers headers = new Headers();
        assertFalse(headers.containsIgnoreCase("foo", "bar"));
        assertFalse(headers.containsIgnoreCase("FOO", "bar"));
        assertFalse(headers.containsIgnoreCase("foo", "BAR"));
        assertFalse(headers.containsIgnoreCase("FOO", "BAR"));
        headers.add("foo", "bar");
        assertTrue(headers.containsIgnoreCase("foo", "bar"));
        assertTrue(headers.containsIgnoreCase("FOO", "bar"));
        assertTrue(headers.containsIgnoreCase("foo", "BAR"));
        assertTrue(headers.containsIgnoreCase("FOO", "BAR"));
    }

    @Test
    void requireThatAddStringWorksAsExpected() {
        Headers headers = new Headers();
        assertNull(headers.get("foo"));
        headers.add("foo", "bar");
        assertEquals(Arrays.asList("bar"), headers.get("foo"));
        headers.add("foo", "baz");
        assertEquals(Arrays.asList("bar", "baz"), headers.get("foo"));
    }

    @Test
    void requireThatAddListWorksAsExpected() {
        Headers headers = new Headers();
        assertNull(headers.get("foo"));
        headers.add("foo", Arrays.asList("bar"));
        assertEquals(Arrays.asList("bar"), headers.get("foo"));
        headers.add("foo", Arrays.asList("baz", "cox"));
        assertEquals(Arrays.asList("bar", "baz", "cox"), headers.get("foo"));
    }

    @Test
    void requireThatAddAllWorksAsExpected() {
        Headers headers = new Headers();
        headers.add("foo", "bar");
        headers.add("bar", "baz");
        assertEquals(Arrays.asList("bar"), headers.get("foo"));
        assertEquals(Arrays.asList("baz"), headers.get("bar"));

        Map<String, List<String>> map = new HashMap<>();
        map.put("foo", Arrays.asList("baz", "cox"));
        map.put("bar", Arrays.asList("cox"));
        headers.addAll(map);

        assertEquals(Arrays.asList("bar", "baz", "cox"), headers.get("foo"));
        assertEquals(Arrays.asList("baz", "cox"), headers.get("bar"));
    }

    @Test
    void requireThatPutStringWorksAsExpected() {
        Headers headers = new Headers();
        assertNull(headers.get("foo"));
        headers.put("foo", "bar");
        assertEquals(Arrays.asList("bar"), headers.get("foo"));
        headers.put("foo", "baz");
        assertEquals(Arrays.asList("baz"), headers.get("foo"));
    }

    @Test
    void requireThatPutListWorksAsExpected() {
        Headers headers = new Headers();
        assertNull(headers.get("foo"));
        headers.put("foo", Arrays.asList("bar"));
        assertEquals(Arrays.asList("bar"), headers.get("foo"));
        headers.put("foo", Arrays.asList("baz", "cox"));
        assertEquals(Arrays.asList("baz", "cox"), headers.get("foo"));
    }

    @Test
    void requireThatPutAllWorksAsExpected() {
        Headers headers = new Headers();
        headers.add("foo", "bar");
        headers.add("bar", "baz");
        assertEquals(Arrays.asList("bar"), headers.get("foo"));
        assertEquals(Arrays.asList("baz"), headers.get("bar"));

        Map<String, List<String>> map = new HashMap<>();
        map.put("foo", Arrays.asList("baz", "cox"));
        map.put("bar", Arrays.asList("cox"));
        headers.putAll(map);

        assertEquals(Arrays.asList("baz", "cox"), headers.get("foo"));
        assertEquals(Arrays.asList("cox"), headers.get("bar"));
    }

    @Test
    void requireThatRemoveWorksAsExpected() {
        Headers headers = new Headers();
        headers.put("foo", Arrays.asList("bar", "baz"));
        assertEquals(Arrays.asList("bar", "baz"), headers.get("foo"));
        assertEquals(Arrays.asList("bar", "baz"), headers.remove("foo"));
        assertNull(headers.get("foo"));
        assertNull(headers.remove("foo"));
    }

    @Test
    void requireThatRemoveStringWorksAsExpected() {
        Headers headers = new Headers();
        headers.put("foo", Arrays.asList("bar", "baz"));
        assertEquals(Arrays.asList("bar", "baz"), headers.get("foo"));
        assertTrue(headers.remove("foo", "bar"));
        assertFalse(headers.remove("foo", "cox"));
        assertEquals(Arrays.asList("baz"), headers.get("foo"));
        assertTrue(headers.remove("foo", "baz"));
        assertFalse(headers.remove("foo", "cox"));
        assertNull(headers.get("foo"));
    }

    @Test
    void requireThatClearWorksAsExpected() {
        Headers headers = new Headers();
        headers.add("foo", "bar");
        headers.add("bar", "baz");
        assertEquals(Arrays.asList("bar"), headers.get("foo"));
        assertEquals(Arrays.asList("baz"), headers.get("bar"));
        headers.clear();
        assertNull(headers.get("foo"));
        assertNull(headers.get("bar"));
    }

    @Test
    void requireThatGetWorksAsExpected() {
        Headers headers = new Headers();
        assertNull(headers.get("foo"));
        headers.add("foo", "bar");
        assertEquals(Arrays.asList("bar"), headers.get("foo"));
    }

    @Test
    void requireThatGetFirstWorksAsExpected() {
        Headers headers = new Headers();
        assertNull(headers.getFirst("foo"));
        headers.add("foo", Arrays.asList("bar", "baz"));
        assertEquals("bar", headers.getFirst("foo"));
    }

    @Test
    void requireThatIsTrueWorksAsExpected() {
        Headers headers = new Headers();
        assertFalse(headers.isTrue("foo"));
        headers.put("foo", Arrays.asList("true"));
        assertTrue(headers.isTrue("foo"));
        headers.put("foo", Arrays.asList("true", "true"));
        assertTrue(headers.isTrue("foo"));
        headers.put("foo", Arrays.asList("true", "false"));
        assertFalse(headers.isTrue("foo"));
        headers.put("foo", Arrays.asList("false", "true"));
        assertFalse(headers.isTrue("foo"));
        headers.put("foo", Arrays.asList("false", "false"));
        assertFalse(headers.isTrue("foo"));
        headers.put("foo", Arrays.asList("false"));
        assertFalse(headers.isTrue("foo"));
    }

    @Test
    void requireThatKeySetWorksAsExpected() {
        Headers headers = new Headers();
        assertEquals(Collections.<Set<String>>emptySet(), headers.keySet());
        headers.add("foo", "bar");
        assertEquals(new HashSet<>(Arrays.asList("foo")), headers.keySet());
        headers.add("bar", "baz");
        assertEquals(new HashSet<>(Arrays.asList("foo", "bar")), headers.keySet());
    }

    @Test
    void requireThatValuesWorksAsExpected() {
        Headers headers = new Headers();
        assertTrue(headers.values().isEmpty());
        headers.add("foo", "bar");
        Collection<List<String>> values = headers.values();
        assertEquals(1, values.size());
        assertTrue(values.contains(Arrays.asList("bar")));

        headers.add("bar", "baz");
        values = headers.values();
        assertEquals(2, values.size());
        assertTrue(values.contains(Arrays.asList("bar")));
        assertTrue(values.contains(Arrays.asList("baz")));
    }

    @Test
    void requireThatEntrySetWorksAsExpected() {
        Headers headers = new Headers();
        assertEquals(Collections.emptySet(), headers.entrySet());
        headers.put("foo", Arrays.asList("bar", "baz"));

        Set<Map.Entry<String, List<String>>> entries = headers.entrySet();
        assertEquals(1, entries.size());
        Map.Entry<String, List<String>> entry = entries.iterator().next();
        assertNotNull(entry);
        assertEquals("foo", entry.getKey());
        assertEquals(Arrays.asList("bar", "baz"), entry.getValue());
    }

    @Test
    void requireThatEntriesWorksAsExpected() {
        Headers headers = new Headers();
        assertEquals(Collections.emptyList(), headers.entries());
        headers.put("foo", Arrays.asList("bar", "baz"));

        List<Map.Entry<String, String>> entries = headers.entries();
        assertEquals(2, entries.size());

        Map.Entry<String, String> entry = entries.get(0);
        assertNotNull(entry);
        assertEquals("foo", entry.getKey());
        assertEquals("bar", entry.getValue());

        assertNotNull(entry = entries.get(1));
        assertEquals("foo", entry.getKey());
        assertEquals("baz", entry.getValue());
    }

    @Test
    void requireThatEntryIsUnmodifiable() {
        Headers headers = new Headers();
        headers.put("foo", "bar");
        Map.Entry<String, String> entry = headers.entries().get(0);
        try {
            entry.setValue("baz");
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    void requireThatEntriesAreUnmodifiable() {
        Headers headers = new Headers();
        headers.put("foo", "bar");
        List<Map.Entry<String, String>> entries = headers.entries();
        try {
            entries.add(new MyEntry());
            fail();
        } catch (UnsupportedOperationException e) {

        }
        try {
            entries.remove(new MyEntry());
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    void requireThatEqualsWorksAsExpected() {
        Headers lhs = new Headers();
        Headers rhs = new Headers();
        assertEquals(lhs, rhs);
        lhs.add("foo", "bar");
        assertNotEquals(lhs, rhs);
        rhs.add("foo", "bar");
        assertEquals(lhs, rhs);
    }

    @Test
    void requireThatHashCodeWorksAsExpected() {
        Headers lhs = new Headers();
        Headers rhs = new Headers();
        assertEquals(lhs.hashCode(), rhs.hashCode());
        lhs.add("foo", "bar");
        assertTrue(lhs.hashCode() != rhs.hashCode());
        rhs.add("foo", "bar");
        assertEquals(lhs.hashCode(), rhs.hashCode());
    }

    private static class MyEntry implements Map.Entry<String, String> {

        @Override
        public String getKey() {
            return "key";
        }

        @Override
        public String getValue() {
            return "value";
        }

        @Override
        public String setValue(String value) {
            return "value";
        }
    }
}
