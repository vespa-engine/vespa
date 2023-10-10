// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.Test;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class ListMapTestCase {

    @Test
    public void testSimple() {
        ListMap<String, String> stringMap = new ListMap<>();
        stringMap.put("foo", "bar");
        stringMap.put("foo", "far");
        stringMap.put("bar", "rab");

        List<String> fooValues = stringMap.get("foo");
        assertEquals(2, fooValues.size());
        assertEquals("bar", fooValues.get(0));
        assertEquals("far", fooValues.get(1));

        List<String> barValues = stringMap.get("bar");
        assertEquals(1, barValues.size());
        assertEquals("rab", barValues.get(0));
    }

    @Test
    public void testAnotherImplementation() {
        ListMap<String, String> stringMap = new ListMap<>(IdentityHashMap.class);
        String foo = "foo";
        String bar = "bar";
        String far = "far";
        String rab = "rab";

        stringMap.put(foo, bar);
        stringMap.put(foo, far);
        stringMap.put(bar, rab);

        List<String> fooValues = stringMap.get(new String("foo"));
        assertEquals(0, fooValues.size());
        fooValues = stringMap.get(foo);
        assertEquals(2, fooValues.size());
        assertEquals("bar", fooValues.get(0));
        assertEquals("far", fooValues.get(1));


        List<String> barValues = stringMap.get(new String("bar"));
        assertEquals(0, barValues.size());
        barValues = stringMap.get(bar);
        assertEquals(1, barValues.size());
        assertEquals("rab", barValues.get(0));
    }

    @SuppressWarnings("serial")
    private static class BoomMap extends HashMap<String, String> {
        @SuppressWarnings("unused")
        BoomMap() {
            throw new RuntimeException();
        }
    }

    @Test
    public void testExplodingImplementation() {
        boolean illegalArgument = false;
        try {
            new ListMap<String, String>(BoomMap.class);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getCause().getClass() == RuntimeException.class);
            illegalArgument = true;
        }
        assertTrue(illegalArgument);
    }

    private static final String A = "A";
    private static final String B = "B";
    private static final String B0 = "b0";

    private ListMap<String, String> initSimpleMap() {
        ListMap<String, String> lm = new ListMap<>();
        lm.put(A, "a0");
        lm.put(A, "a1");
        lm.put(B, B0);
        lm.put(B, "b1");
        lm.put("C", "c");
        lm.put("D", "d");
        return lm;
    }

    @Test
    public void testRemoval() {
        ListMap<String, String> lm = initSimpleMap();
        assertEquals(2, lm.getList(A).size());
        assertEquals(4, lm.entrySet().size());
        lm.removeAll(A);
        assertEquals(3, lm.entrySet().size());
        assertEquals(0, lm.getList(A).size());
        assertEquals(2, lm.getList(B).size());
        assertTrue(lm.removeValue(B, B0));
        assertFalse(lm.removeValue(B, B0));
        assertEquals(1, lm.getList(B).size());
        assertEquals(3, lm.entrySet().size());
    }

    @Test
    public void testGetSet() {
        ListMap<String, String> lm = initSimpleMap();
        lm.removeAll(B);
        Set<Map.Entry<String, List<String>>> l = lm.entrySet();
        assertEquals(3, l.size());
        boolean hasA = false;
        boolean hasB = false;
        for (Map.Entry<String, List<String>> e : l) {
            if (e.getKey().equals(A)) {
                hasA = true;
            } else if (e.getKey().equals(B)) {
                hasB = true;
            }
        }
        assertTrue(hasA);
        assertFalse(hasB);
    }

    @Test
    public void testFreeze() {
        ListMap<String, String> map = initSimpleMap();
        map.freeze();
        try {
            map.put("key", "value");
            fail("Expected exception");
        }
        catch (Exception expected) {
        }
        try {
            map.entrySet().iterator().next().getValue().add("foo");
            fail("Expected exception");
        }
        catch (Exception expected) {
        }
    }

}
