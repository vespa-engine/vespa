// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
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
 */
public class HeaderFieldsTestCase {

    @Test
    void requireThatSizeWorksAsExpected() {
        HeaderFields headers = new HeaderFields();
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
        HeaderFields headers = new HeaderFields();
        assertTrue(headers.isEmpty());
        headers.add("foo", "bar");
        assertFalse(headers.isEmpty());
        headers.remove("foo");
        assertTrue(headers.isEmpty());
    }

    @Test
    void requireThatContainsKeyWorksAsExpected() {
        HeaderFields headers = new HeaderFields();
        assertFalse(headers.containsKey("foo"));
        assertFalse(headers.containsKey("FOO"));
        headers.add("foo", "bar");
        assertTrue(headers.containsKey("foo"));
        assertTrue(headers.containsKey("FOO"));
    }

    @Test
    void requireThatContainsValueWorksAsExpected() {
        HeaderFields headers = new HeaderFields();
        assertFalse(headers.containsValue(Arrays.asList("bar")));
        headers.add("foo", "bar");
        assertTrue(headers.containsValue(Arrays.asList("bar")));
    }

    @Test
    void requireThatContainsWorksAsExpected() {
        HeaderFields headers = new HeaderFields();
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
        HeaderFields headers = new HeaderFields();
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
        HeaderFields headers = new HeaderFields();
        assertNull(headers.get("foo"));
        headers.add("foo", "bar");
        assertEquals(Arrays.asList("bar"), headers.get("foo"));
        headers.add("foo", "baz");
        assertEquals(Arrays.asList("bar", "baz"), headers.get("foo"));
    }

    @Test
    void requireThatAddListWorksAsExpected() {
        HeaderFields headers = new HeaderFields();
        assertNull(headers.get("foo"));
        headers.add("foo", Arrays.asList("bar"));
        assertEquals(Arrays.asList("bar"), headers.get("foo"));
        headers.add("foo", Arrays.asList("baz", "cox"));
        assertEquals(Arrays.asList("bar", "baz", "cox"), headers.get("foo"));
    }

    @Test
    void requireThatAddAllWorksAsExpected() {
        HeaderFields headers = new HeaderFields();
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
        HeaderFields headers = new HeaderFields();
        assertNull(headers.get("foo"));
        headers.put("foo", "bar");
        assertEquals(Arrays.asList("bar"), headers.get("foo"));
        headers.put("foo", "baz");
        assertEquals(Arrays.asList("baz"), headers.get("foo"));
    }

    @Test
    void requireThatPutListWorksAsExpected() {
        HeaderFields headers = new HeaderFields();
        assertNull(headers.get("foo"));
        headers.put("foo", Arrays.asList("bar"));
        assertEquals(Arrays.asList("bar"), headers.get("foo"));
        headers.put("foo", Arrays.asList("baz", "cox"));
        assertEquals(Arrays.asList("baz", "cox"), headers.get("foo"));
    }

    @Test
    void requireThatPutAllWorksAsExpected() {
        HeaderFields headers = new HeaderFields();
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
        HeaderFields headers = new HeaderFields();
        headers.put("foo", Arrays.asList("bar", "baz"));
        assertEquals(Arrays.asList("bar", "baz"), headers.get("foo"));
        assertEquals(Arrays.asList("bar", "baz"), headers.remove("foo"));
        assertNull(headers.get("foo"));
        assertNull(headers.remove("foo"));
    }

    @Test
    void requireThatRemoveStringWorksAsExpected() {
        HeaderFields headers = new HeaderFields();
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
        HeaderFields headers = new HeaderFields();
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
        HeaderFields headers = new HeaderFields();
        assertNull(headers.get("foo"));
        headers.add("foo", "bar");
        assertEquals(Arrays.asList("bar"), headers.get("foo"));
    }

    @Test
    void requireThatGetFirstWorksAsExpected() {
        HeaderFields headers = new HeaderFields();
        assertNull(headers.getFirst("foo"));
        headers.add("foo", Arrays.asList("bar", "baz"));
        assertEquals("bar", headers.getFirst("foo"));
    }

    @Test
    void requireThatIsTrueWorksAsExpected() {
        HeaderFields headers = new HeaderFields();
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
        HeaderFields headers = new HeaderFields();
        assertTrue(headers.keySet().isEmpty());
        headers.add("foo", "bar");
        assertEquals(new HashSet<>(Arrays.asList("foo")), headers.keySet());
        headers.add("bar", "baz");
        assertEquals(new HashSet<>(Arrays.asList("foo", "bar")), headers.keySet());
    }

    @Test
    void requireThatValuesWorksAsExpected() {
        HeaderFields headers = new HeaderFields();
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
        HeaderFields headers = new HeaderFields();
        assertTrue(headers.entrySet().isEmpty());
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
        HeaderFields headers = new HeaderFields();
        assertTrue(headers.entries().isEmpty());
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
        HeaderFields headers = new HeaderFields();
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
        HeaderFields headers = new HeaderFields();
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
        HeaderFields lhs = new HeaderFields();
        HeaderFields rhs = new HeaderFields();
        assertEquals(lhs, rhs);
        lhs.add("foo", "bar");
        assertNotEquals(lhs, rhs);
        rhs.add("foo", "bar");
        assertEquals(lhs, rhs);
    }

    @Test
    void requireThatHashCodeWorksAsExpected() {
        HeaderFields lhs = new HeaderFields();
        HeaderFields rhs = new HeaderFields();
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
