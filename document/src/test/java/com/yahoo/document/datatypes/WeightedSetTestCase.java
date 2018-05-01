// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Einar M R Rosenvinge
 */
public class WeightedSetTestCase {

    @Test
    public void testSet() {
        WeightedSet<StringFieldValue> wset = new WeightedSet<>(DataType.TAG);

        //ADD:

        Object ok;
        ok = wset.put(new StringFieldValue("this is a test"), 5);
        assertNull(ok);
        ok = wset.put(new StringFieldValue("this is a test"), 10);
        assertEquals(5, ok);

        assertEquals(1, wset.size());
        assertEquals(wset.get(new StringFieldValue("this is a test")), Integer.valueOf(10));

        //REMOVE:

        ok = wset.put(new StringFieldValue("another test"), 7);
        assertNull(ok);

        assertEquals(2, wset.size());

        ok = wset.remove(new StringFieldValue("this is a test"));
        assertNotNull(ok);
        assertEquals(1, wset.size());

        ok = wset.remove(new StringFieldValue("another test"));
        assertNotNull(ok);
        assertEquals(0, wset.size());

        //CONTAINS:

        wset.put(new StringFieldValue("ballooo"), 50);
        wset.put(new StringFieldValue("bananaa"), 51);

        ok = wset.containsKey(new StringFieldValue("bananaa"));
        assertEquals(true, ok);
        ok = wset.containsKey(new StringFieldValue("ballooo"));
        assertEquals(true, ok);

        //EQUALS // Make sure order of input doesn't affect equals
        WeightedSet<StringFieldValue> wset2 = new WeightedSet<>(DataType.TAG);
        wset2.put(new StringFieldValue("bananaa"), 51);
        wset2.put(new StringFieldValue("ballooo"), 50);
        assertEquals(wset, wset2);

    }

    @Test
    public void testAssignDoesNotIgnoreSpecialProperties() {
        DataType type = DataType.getWeightedSet(DataType.STRING);
        WeightedSet<StringFieldValue> set = new WeightedSet<>(type);
        set.put(new StringFieldValue("hello"), 5);
        set.put(new StringFieldValue("aba"), 10);
        assertEquals(2, set.size());
        assertEquals(Integer.valueOf(5), set.get(new StringFieldValue("hello")));
        assertEquals(Integer.valueOf(10), set.get(new StringFieldValue("aba")));

        DataType type2 = DataType.getWeightedSet(DataType.STRING, true, true);
        WeightedSet<StringFieldValue> set2 = new WeightedSet<>(type2);
        set2.put(new StringFieldValue("hi"), 6);
        set2.put(new StringFieldValue("bye"), 13);
        set2.put(new StringFieldValue("see you"), 15);
        assertEquals(3, set2.size());
        assertEquals(Integer.valueOf(6), set2.get(new StringFieldValue("hi")));
        assertEquals(Integer.valueOf(13), set2.get(new StringFieldValue("bye")));
        assertEquals(Integer.valueOf(15), set2.get(new StringFieldValue("see you")));

        try {
            set.assign(set2);
            fail("it shouldn't be possible to assign a weighted set to another when types differ");
        } catch (IllegalArgumentException iae) {
            //success
        }

        assertEquals(2, set.size());
        assertEquals(Integer.valueOf(5), set.get(new StringFieldValue("hello")));
        assertEquals(Integer.valueOf(10), set.get(new StringFieldValue("aba")));
    }

    @Test
    public void testWrappedMap() {
        WeightedSet<StringFieldValue> ws = new WeightedSet<>(DataType.getWeightedSet(DataType.STRING));
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("foo", 1);
        map.put("bar", 2);

        ws.assign(map);

        assertEquals(2, ws.size());
        assertEquals(2, map.size());

        assertTrue(ws.containsKey(new StringFieldValue("foo")));
        assertTrue(ws.containsKey(new StringFieldValue("bar")));
        assertFalse(ws.containsKey(new StringFieldValue("babar")));

        ws.put(new StringFieldValue("banana"), 55);

        assertEquals(3, ws.size());
        assertEquals(3, map.size());

        assertTrue(ws.containsValue(55));
        assertFalse(ws.isEmpty());
        ws.clear();
        assertEquals(0, ws.size());
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertTrue(ws.isEmpty());

        Map<StringFieldValue, Integer> tmp = new LinkedHashMap<>();
        tmp.put(new StringFieldValue("cocacola"), 999);
        tmp.put(new StringFieldValue("pepsicola"), 99999);
        ws.putAll(tmp);

        assertEquals(2, ws.size());
        assertEquals(2, map.size());

        ws.remove(new StringFieldValue("cocacola"));

        assertEquals(1, ws.size());
        assertEquals(1, map.size());

        assertTrue(ws.contains(new StringFieldValue("pepsicola")));

        ws.put(new StringFieldValue("solo"), 4);

        assertEquals(2, ws.size());
        assertEquals(2, map.size());

        ws.add(new StringFieldValue("sitronbrus"));

        assertEquals(3, ws.size());
        assertEquals(3, map.size());

        assertEquals(Integer.valueOf(1), ws.get(new StringFieldValue("sitronbrus")));
    }

    @Test
    public void testAssigningWrappedSetToMapFieldValue() {
        WeightedSet<StringFieldValue> weightedSet = new WeightedSet<>(DataType.getWeightedSet(DataType.STRING));
        WeightedSet<StringFieldValue> assignmentTarget = new WeightedSet<>(DataType.getWeightedSet(DataType.STRING));
        Map<String, Integer> rawMap = new LinkedHashMap<>();
        rawMap.put("foo", 1);
        rawMap.put("bar", 2);
        weightedSet.assign(rawMap);
        assignmentTarget.assign(weightedSet);
        assertEquals(2, assignmentTarget.size());
        assertEquals(Integer.valueOf(1), assignmentTarget.get(new StringFieldValue("foo")));
        assertEquals(Integer.valueOf(2), assignmentTarget.get(new StringFieldValue("bar")));
    }

}
