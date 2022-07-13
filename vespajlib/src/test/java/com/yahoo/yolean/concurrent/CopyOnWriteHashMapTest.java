// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.concurrent;

import org.junit.Test;

import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author baldersheim
 * @since 5.2
 */
public class CopyOnWriteHashMapTest {

    @Test
    public void requireThatAccessorsWork() {
        Map<String, String> map = new CopyOnWriteHashMap<>();
        assertEquals(0, map.size());
        assertEquals(true, map.isEmpty());
        assertEquals(false, map.containsKey("fooKey"));
        assertEquals(false, map.containsValue("fooVal"));
        assertNull(map.get("fooKey"));
        assertNull(map.remove("fooKey"));
        assertEquals(0, map.keySet().size());
        assertEquals(0, map.entrySet().size());
        assertEquals(0, map.values().size());

        map.put("fooKey", "fooVal");
        assertEquals(1, map.size());
        assertEquals(false, map.isEmpty());
        assertEquals(true, map.containsKey("fooKey"));
        assertEquals(true, map.containsValue("fooVal"));
        assertEquals("fooVal", map.get("fooKey"));
        assertEquals(1, map.keySet().size());
        assertEquals(1, map.entrySet().size());
        assertEquals(1, map.values().size());

        map.put("barKey", "barVal");
        assertEquals(2, map.size());
        assertEquals(false, map.isEmpty());
        assertEquals(true, map.containsKey("fooKey"));
        assertEquals(true, map.containsKey("barKey"));
        assertEquals(true, map.containsValue("fooVal"));
        assertEquals(true, map.containsValue("barVal"));
        assertEquals("fooVal", map.get("fooKey"));
        assertEquals("barVal", map.get("barKey"));
        assertEquals(2, map.keySet().size());
        assertEquals(2, map.entrySet().size());
        assertEquals(2, map.values().size());

        assertEquals("fooVal", map.remove("fooKey"));
        assertEquals(1, map.size());
        assertEquals(false, map.isEmpty());
        assertEquals(false, map.containsKey("fooKey"));
        assertEquals(true, map.containsKey("barKey"));
        assertEquals(false, map.containsValue("fooVal"));
        assertEquals(true, map.containsValue("barVal"));
        assertNull(map.get("fooKey"));
        assertEquals("barVal", map.get("barKey"));
        assertEquals(1, map.keySet().size());
        assertEquals(1, map.entrySet().size());
        assertEquals(1, map.values().size());
    }

    @Test
    public void requireThatEntrySetDoesNotReflectConcurrentModifications() {
        Map<String, String> map = new CopyOnWriteHashMap<>();
        map.put("fooKey", "fooVal");

        Iterator<Map.Entry<String, String>> it = map.entrySet().iterator();
        assertEquals("fooVal", map.remove("fooKey"));

        assertTrue(it.hasNext());
        Map.Entry<String, String> entry = it.next();
        assertEquals("fooKey", entry.getKey());
        assertEquals("fooVal", entry.getValue());
    }

    @Test
    public void requireThatKeySetDoesNotReflectConcurrentModifications() {
        Map<String, String> map = new CopyOnWriteHashMap<>();
        map.put("fooKey", "fooVal");

        Iterator<String> it = map.keySet().iterator();
        assertEquals("fooVal", map.remove("fooKey"));

        assertTrue(it.hasNext());
        assertEquals("fooKey", it.next());
    }

    @Test
    public void requireThatValuesDoNotReflectConcurrentModifications() {
        Map<String, String> map = new CopyOnWriteHashMap<>();
        map.put("fooKey", "fooVal");

        Iterator<String> it = map.values().iterator();
        assertEquals("fooVal", map.remove("fooKey"));

        assertTrue(it.hasNext());
        assertEquals("fooVal", it.next());
    }
}
