// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author baldersheim
 */
public class SearchResultTestCase {

    @Test
    public void requireThatHitsOrderWell() {
        SearchResult.Hit a = new SearchResult.Hit("a", 0);
        SearchResult.Hit b = new SearchResult.Hit("b", 0.1);
        SearchResult.Hit c = new SearchResult.Hit("c", 1.0);
        SearchResult.Hit bb = new SearchResult.Hit("b2", 0.1);
        assertEquals(0, a.compareTo(a));
        assertTrue(a.compareTo(b) > 0);
        assertTrue(a.compareTo(c) > 0);
        assertTrue(b.compareTo(a) < 0);
        assertEquals(0, b.compareTo(bb));
        assertEquals(0, bb.compareTo(b));
        assertTrue(b.compareTo(c) > 0);
        assertTrue(c.compareTo(a) < 0);
        assertTrue(c.compareTo(b) < 0);

        byte [] b1 = {0x00};
        byte [] b2 = {0x07};
        byte [] b3 = {0x7f};
        byte [] b4 = {(byte)0x80};
        byte [] b5 = {(byte)0xb1};
        byte [] b6 = {(byte)0xff};

        assertEquals(0x00, b1[0]);
        assertEquals(0x07, b2[0]);
        assertEquals(0x7f, b3[0]);
        assertEquals(0x80, ((int)b4[0]) & 0xff);
        assertEquals(0xb1, ((int)b5[0]) & 0xff);
        assertEquals(0xff, ((int)b6[0]) & 0xff);
        SearchResult.Hit h1 = new SearchResult.HitWithSortBlob(a, b1);
        SearchResult.Hit h2 = new SearchResult.HitWithSortBlob(a, b2);
        SearchResult.Hit h3 = new SearchResult.HitWithSortBlob(a, b3);
        SearchResult.Hit h4 = new SearchResult.HitWithSortBlob(a, b4);
        SearchResult.Hit h5 = new SearchResult.HitWithSortBlob(a, b5);
        SearchResult.Hit h6 = new SearchResult.HitWithSortBlob(a, b6);

        assertEquals(0, h1.compareTo(h1));
        assertTrue(h1.compareTo(h2) < 0);
        assertTrue(h1.compareTo(h3) < 0);
        assertTrue(h1.compareTo(h4) < 0);
        assertTrue(h1.compareTo(h5) < 0);
        assertTrue(h1.compareTo(h6) < 0);

        assertTrue(h2.compareTo(h1) > 0);
        assertEquals(0, h2.compareTo(h2));
        assertTrue(h2.compareTo(h3) < 0);
        assertTrue(h2.compareTo(h4) < 0);
        assertTrue(h2.compareTo(h5) < 0);
        assertTrue(h2.compareTo(h6) < 0);

        assertTrue(h3.compareTo(h1) > 0);
        assertTrue(h3.compareTo(h2) > 0);
        assertEquals(0, h3.compareTo(h3));
        assertTrue(h3.compareTo(h4) < 0);
        assertTrue(h3.compareTo(h5) < 0);
        assertTrue(h3.compareTo(h6) < 0);

        assertTrue(h4.compareTo(h1) > 0);
        assertTrue(h4.compareTo(h2) > 0);
        assertTrue(h4.compareTo(h3) > 0);
        assertEquals(0, h4.compareTo(h4));
        assertTrue(h4.compareTo(h5) < 0);
        assertTrue(h4.compareTo(h6) < 0);

        assertTrue(h5.compareTo(h1) > 0);
        assertTrue(h5.compareTo(h2) > 0);
        assertTrue(h5.compareTo(h3) > 0);
        assertTrue(h5.compareTo(h4) > 0);
        assertEquals(0, h5.compareTo(h5));
        assertTrue(h5.compareTo(h6) < 0);

        assertTrue(h6.compareTo(h1) > 0);
        assertTrue(h6.compareTo(h2) > 0);
        assertTrue(h6.compareTo(h3) > 0);
        assertTrue(h6.compareTo(h4) > 0);
        assertTrue(h6.compareTo(h5) > 0);
        assertEquals(0, h6.compareTo(h6));
    }
}
