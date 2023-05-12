// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.access.helpers;

import com.yahoo.collections.Hashlet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author arnej
 */
public class MatchFeatureFilterTest {

    Hashlet<String,Integer> makeHash() {
        var h = new Hashlet<String,Integer>();
        h.put("foo", 0);
        h.put("bar", 1);
        h.put("baz", 2);
        h.put("four", 4);
        h.put("five", 5);
        return h;
    }

    @Test
    void testFiltering() {
        var h1 = makeHash();
        var h2 = makeHash();
        var h3 = makeHash();
        var f1 = new MatchFeatureFilter(List.of("foo", "baz", "four"));
        var f2 = new MatchFeatureFilter(Set.of("bar", "five"));
        var f3 = new MatchFeatureFilter(List.of("not", "bar", "nope"));
        var fAll = new MatchFeatureFilter(Set.of("foo", "bar", "baz", "four", "five"));

        var h4 = f1.apply(h1);
        var h5 = f1.apply(h2);
        var h6 = f1.apply(h1);
        var h7 = f1.apply(h3);
        assertEquals(2, h4.size());
        assertEquals(1, h4.get("bar"));
        assertEquals(5, h4.get("five"));
        assertEquals(h4, h5);
        assertEquals(h4, h6);
        assertEquals(h4, h7);
        // check that we get same instance out if we put the same instance in (only)
        assertFalse(h4 == h5);
        assertTrue(h4 == h6);
        assertFalse(h4 == h7);
        assertTrue(h5 == f1.apply(h2));
        assertTrue(h7 == f1.apply(h3));

        var h8 = f2.apply(h1);
        assertEquals(3, h8.size());
        assertEquals(0, h8.get("foo"));
        assertEquals(2, h8.get("baz"));
        assertEquals(4, h8.get("four"));
        assertTrue(h8 == f2.apply(h1));

        var h9 = f3.apply(h1);
        assertEquals(4, h9.size());
        assertNull(h9.get("bar"));

        var empty = fAll.apply(h1);
        assertEquals(0, empty.size());
    }

}
