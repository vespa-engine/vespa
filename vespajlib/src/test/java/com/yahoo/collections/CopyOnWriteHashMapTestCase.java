// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class CopyOnWriteHashMapTestCase {

    @Test
    public void testModifySourceFirst() {
        CopyOnWriteHashMap<String, String> map = new CopyOnWriteHashMap<>();
        map.put("a", "a1");
        map.put("b", "b1");
        CopyOnWriteHashMap<String,String> clone = map.clone();
        map.put("c", "c1");
        clone.remove("a");
        clone.put("b", "b2");
        clone.put("d", "d2");

        assertEquals(3, map.size());
        assertEquals("a1", map.get("a"));
        assertEquals("b1", map.get("b"));
        assertEquals("c1", map.get("c"));

        assertEquals(2, clone.size());
        assertEquals("b2", clone.get("b"));
        assertEquals("d2", clone.get("d"));
    }

    @Test
    public void testModifyTargetFirst() {
        CopyOnWriteHashMap<String, String> map = new CopyOnWriteHashMap<>();
        map.put("a", "a1");
        map.put("b", "b1");
        CopyOnWriteHashMap<String,String> clone = map.clone();
        clone.remove("a");
        map.put("c", "c1");
        clone.put("b", "b2");
        clone.put("d", "d2");

        assertEquals(3, map.size());
        assertEquals("a1", map.get("a"));
        assertEquals("b1", map.get("b"));
        assertEquals("c1", map.get("c"));

        assertEquals(2, clone.size());
        assertEquals("b2", clone.get("b"));
        assertEquals("d2", clone.get("d"));
    }

    @Test
    public void testCallEntrySetThenModify() {
        CopyOnWriteHashMap<String, String> map = new CopyOnWriteHashMap<>();
        map.put("a", "a1");
        map.entrySet();
        CopyOnWriteHashMap<String,String> clone = map.clone();
        clone.put("b", "b1");
        assertEquals(2, clone.size());
    }

}
