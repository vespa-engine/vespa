package com.yahoo.prelude.fastsearch.test;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
        tester.setResponding(FastSearcherTester.selfHostname, false);
        assertEquals("1 ping request, 0 search requests", 1, tester.requestCount(FastSearcherTester.selfHostname, 9999));
        tester.search("?query=test&dispatch.direct=true");
        assertEquals("1 ping request, 0 search requests", 1, tester.requestCount(FastSearcherTester.selfHostname, 9999));
        tester.setResponding(FastSearcherTester.selfHostname, true);
        assertEquals("2 ping requests, 0 search request", 2, tester.requestCount(FastSearcherTester.selfHostname, 9999));
        tester.search("?query=test&dispatch.direct=true&nocache");
        assertEquals("2 ping requests, 1 search request", 3, tester.requestCount(FastSearcherTester.selfHostname, 9999));
    }

}
