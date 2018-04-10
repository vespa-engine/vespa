// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests that FastSearcher will bypass dispatch when the conditions are right
 * 
 * @author bratseth
 */
public class DirectSearchTestCase {

    @Test
    public void testDirectSearchEnabled() {
        FastSearcherTester tester = new FastSearcherTester(1, FastSearcherTester.selfHostname + ":9999:0");
        tester.search("?query=test&dispatch.direct=true");
        assertEquals("The FastSearcher has used the local search node connection", 1, tester.requestCount(FastSearcherTester.selfHostname, 9999));
    }

    @Test
    public void testDirectSearchDisabled() {
        FastSearcherTester tester = new FastSearcherTester(1, FastSearcherTester.selfHostname + ":9999:0");
        tester.search("?query=test&dispatch.direct=false");
        assertEquals(0, tester.requestCount(FastSearcherTester.selfHostname, 9999));
    }

    @Test
    public void testDirectSearchDisabledByDefault() {
        FastSearcherTester tester = new FastSearcherTester(1, FastSearcherTester.selfHostname + ":9999:0");
        tester.search("?query=test");
        assertEquals(0, tester.requestCount(FastSearcherTester.selfHostname, 9999));
    }

    @Test
    public void testNoDirectSearchWhenMoreSearchNodesThanContainers() {
        FastSearcherTester tester = new FastSearcherTester(1, FastSearcherTester.selfHostname + ":9999:0", "otherhost:9999:1");
        tester.search("?query=test&dispatch.direct=true");
        assertEquals(0, tester.requestCount(FastSearcherTester.selfHostname, 9999));
    }

    @Test
    public void testDirectSearchWhenMultipleGroupsAndEnoughContainers() {
        FastSearcherTester tester = new FastSearcherTester(2, FastSearcherTester.selfHostname + ":9999:0", "otherhost:9999:1");
        tester.search("?query=test&dispatch.direct=true");
        assertEquals(1, tester.requestCount(FastSearcherTester.selfHostname, 9999));
    }

    @Test
    public void testNoDirectSearchWhenMultipleNodesPerGroup() {
        FastSearcherTester tester = new FastSearcherTester(2, FastSearcherTester.selfHostname + ":9999:0", "otherhost:9999:0");
        tester.search("?query=test&dispatch.direct=true");
        assertEquals(0, tester.requestCount(FastSearcherTester.selfHostname, 9999));
    }

    @Test
    public void testNoDirectSearchWhenLocalNodeIsDown() {
        FastSearcherTester tester = new FastSearcherTester(2, FastSearcherTester.selfHostname + ":9999:0", "otherhost:9999:1");
        assertTrue(tester.vipStatus().isInRotation());
        tester.setResponding(FastSearcherTester.selfHostname, false);
        assertFalse(tester.vipStatus().isInRotation());
        assertEquals("1 ping request, 0 search requests", 1, tester.requestCount(FastSearcherTester.selfHostname, 9999));
        tester.search("?query=test&dispatch.direct=true&nocache");
        assertEquals("1 ping request, 0 search requests", 1, tester.requestCount(FastSearcherTester.selfHostname, 9999));
        tester.setResponding(FastSearcherTester.selfHostname, true);
        assertTrue(tester.vipStatus().isInRotation());
        assertEquals("2 ping requests, 0 search request", 2, tester.requestCount(FastSearcherTester.selfHostname, 9999));
        tester.search("?query=test&dispatch.direct=true&nocache");
        assertEquals("2 ping requests, 1 search request", 3, tester.requestCount(FastSearcherTester.selfHostname, 9999));
    }

    @Test
    public void testNoDirectDispatchWhenInsufficientCoverage() {
        FastSearcherTester tester = new FastSearcherTester(3,
                                                           FastSearcherTester.selfHostname + ":9999:0",
                                                           "host1:9999:1",
                                                           "host2:9999:2");
        double k = 38.78955; // multiply all document counts by some number > 1 to test that we compute % correctly

        tester.setActiveDocuments(FastSearcherTester.selfHostname, (long) (96 * k));
        tester.setActiveDocuments("host1", (long) (100 * k));
        tester.setActiveDocuments("host2", (long) (100 * k));
        assertEquals("1 ping request, 0 search requests", 1, tester.requestCount(FastSearcherTester.selfHostname, 9999));
        tester.search("?query=test&dispatch.direct=true&nocache");
        assertEquals("Still 1 ping request, 0 search requests because the default coverage is 97%, and we only have 96% locally",
                     1, tester.requestCount(FastSearcherTester.selfHostname, 9999));
        tester.waitForInRotationIs(false);

        tester.setActiveDocuments(FastSearcherTester.selfHostname, (long) (99 * k));
        assertEquals("2 ping request, 0 search requests", 2, tester.requestCount(FastSearcherTester.selfHostname, 9999));
        tester.search("?query=test&dispatch.direct=true&nocache");
        assertEquals("2 ping request, 1 search requests because we now have 99% locally",
                     3, tester.requestCount(FastSearcherTester.selfHostname, 9999));
        tester.waitForInRotationIs(true);

        tester.setActiveDocuments("host1", (long) (104 * k));
        assertEquals("2 ping request, 1 search requests", 3, tester.requestCount(FastSearcherTester.selfHostname, 9999));
        tester.search("?query=test&dispatch.direct=true&nocache");
        assertEquals("2 ping request, 2 search requests because 99/((104+100)/2) > 0.97",
                     4, tester.requestCount(FastSearcherTester.selfHostname, 9999));
        tester.waitForInRotationIs(true);

        tester.setActiveDocuments("host2", (long) (102 * k));
        assertEquals("2 ping request, 2 search requests", 4, tester.requestCount(FastSearcherTester.selfHostname, 9999));
        tester.search("?query=test&dispatch.direct=true&nocache");
        assertEquals("Still 2 ping request, 2 search requests because 99/((104+102)/2) < 0.97",
                     4, tester.requestCount(FastSearcherTester.selfHostname, 9999));
        tester.waitForInRotationIs(false);
    }
    
    @Test
    public void testCoverageWithSingleGroup() {
        FastSearcherTester tester = new FastSearcherTester(1, FastSearcherTester.selfHostname + ":9999:0");

        tester.setActiveDocuments(FastSearcherTester.selfHostname, 100);
        assertEquals("1 ping request, 0 search requests", 1, tester.requestCount(FastSearcherTester.selfHostname, 9999));
        tester.search("?query=test&dispatch.direct=true&nocache");
        assertEquals("1 ping request, 1 search requests", 2, tester.requestCount(FastSearcherTester.selfHostname, 9999));
    }

}
