// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.state;

import org.junit.Test;

import java.text.ParseException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NodeStateTestCase {

    @Test
    public void testOrdinals() {
        NodeType nt = NodeType.STORAGE;

        // All states are above maintenance
        assertFalse(new NodeState(nt, State.MAINTENANCE).above(new NodeState(nt, State.MAINTENANCE)));
        assertTrue(new NodeState(nt, State.DOWN).above(new NodeState(nt, State.MAINTENANCE)));
        assertTrue(new NodeState(nt, State.STOPPING).above(new NodeState(nt, State.MAINTENANCE)));
        assertTrue(new NodeState(nt, State.INITIALIZING).above(new NodeState(nt, State.MAINTENANCE)));
        assertTrue(new NodeState(nt, State.RETIRED).above(new NodeState(nt, State.MAINTENANCE)));
        assertTrue(new NodeState(nt, State.UP).above(new NodeState(nt, State.MAINTENANCE)));

        // Most are above down
        assertFalse(new NodeState(nt, State.MAINTENANCE).above(new NodeState(nt, State.DOWN)));
        assertFalse(new NodeState(nt, State.DOWN).above(new NodeState(nt, State.DOWN)));
        assertTrue(new NodeState(nt, State.STOPPING).above(new NodeState(nt, State.DOWN)));
        assertTrue(new NodeState(nt, State.INITIALIZING).above(new NodeState(nt, State.DOWN)));
        assertTrue(new NodeState(nt, State.RETIRED).above(new NodeState(nt, State.DOWN)));
        assertTrue(new NodeState(nt, State.UP).above(new NodeState(nt, State.DOWN)));

        // Only up is above retired
        assertFalse(new NodeState(nt, State.MAINTENANCE).above(new NodeState(nt, State.RETIRED)));
        assertFalse(new NodeState(nt, State.DOWN).above(new NodeState(nt, State.RETIRED)));
        assertFalse(new NodeState(nt, State.STOPPING).above(new NodeState(nt, State.RETIRED)));
        assertFalse(new NodeState(nt, State.INITIALIZING).above(new NodeState(nt, State.RETIRED)));
        assertFalse(new NodeState(nt, State.RETIRED).above(new NodeState(nt, State.RETIRED)));
        assertTrue(new NodeState(nt, State.UP).above(new NodeState(nt, State.RETIRED)));
    }

    @Test
    public void testTrivialitiesToIncreaseCoverage() throws ParseException {
        NodeState ns = new NodeState(NodeType.STORAGE, State.UP);
        assertEquals(1, ns.getReliability());
        assertEquals(false, ns.isAnyDiskDown());

        assertEquals(ns.setReliability(2).serialize(), NodeState.deserialize(NodeType.STORAGE, "r:2").serialize());
        assertEquals(ns.setDiskCount(1).serialize(), NodeState.deserialize(NodeType.STORAGE, "r:2 d:1").serialize());
        assertEquals(ns.setReliability(1).serialize(), NodeState.deserialize(NodeType.STORAGE, "d:1").serialize());

        assertEquals(ns.setDiskState(0, new DiskState(State.DOWN, "", 1)), NodeState.deserialize(NodeType.STORAGE, "s:u d:1 d.0.s:d"));
        assertEquals(ns, NodeState.deserialize(NodeType.STORAGE, "s:u d:1 d.0:d"));
    }

    @Test
    public void testDiskState() throws ParseException {
        NodeState ns = NodeState.deserialize(NodeType.STORAGE, "s:m");
        assertEquals(new DiskState(State.UP, "", 1), ns.getDiskState(0));
        assertEquals(new DiskState(State.UP, "", 1), ns.getDiskState(1));
        assertEquals(new DiskState(State.UP, "", 1), ns.getDiskState(100));

        ns.setDiskCount(2).setDiskState(1, new DiskState(State.DOWN, "bad disk", 1));
        assertEquals(new DiskState(State.UP, "", 1), ns.getDiskState(0));
        assertEquals(new DiskState(State.DOWN, "bad disk", 1), ns.getDiskState(1));

        List<DiskState> diskStates = ns.getDiskStates();
        assertEquals(2, diskStates.size());
        for (int i=0; i<diskStates.size(); i++) {
            DiskState diskState = diskStates.get(i);
            if (i==1) {
                assertEquals(new DiskState(State.DOWN, "bad disk", 1), diskState);
            } else {
                assertEquals(new DiskState(State.UP, "", 1), diskState);
            }
        }

        try {
            NodeState.deserialize(NodeType.STORAGE, "s:m").setDiskCount(-1);
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            NodeState.deserialize(NodeType.STORAGE, "s:m").setDiskState(1, new DiskState(State.DOWN, "bad disk", 1));
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            NodeState.deserialize(NodeType.STORAGE, "s:m").setDiskCount(2).setDiskState(1, new DiskState(State.DOWN, "bad disk", 1)).getDiskState(100);
            assertTrue("Should fail", false);
        } catch (Exception e) {}
    }

    @Test
    public void testSerialization() throws ParseException {
        NodeState ns = new NodeState(NodeType.STORAGE, State.MAINTENANCE);
        assertEquals("s:m", ns.serialize(false));
        assertEquals("s:m", ns.serialize(true));
        assertEquals(ns, NodeState.deserialize(NodeType.STORAGE, "s:m"));
        assertEquals(ns, NodeState.deserialize(NodeType.STORAGE, "s:m c:1.0 r:1 d:0 t:0"));

        NodeState nsd = new NodeState(NodeType.DISTRIBUTOR, State.MAINTENANCE);
        assertEquals(nsd, NodeState.deserialize(NodeType.DISTRIBUTOR, "s:m"));
        assertEquals(nsd, NodeState.deserialize(NodeType.DISTRIBUTOR, "s:m c:2.0 r:2 d:2")); // Ignore capacity, reliability, and disk count for distributors

        assertEquals(ns, NodeState.deserialize(NodeType.STORAGE, ": s:m sbadkey:u bbadkey:2 cbadkey:2.0 rbadkey:2 ibadkey:0.5 tbadkey:2 mbadkey:message dbadkey:2 unknownkey:somevalue"));
        try {
            NodeState.deserialize(NodeType.STORAGE, "s:m badtokenwithoutcolon");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            NodeState.deserialize(NodeType.STORAGE, "s:m c:badvalue");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            NodeState.deserialize(NodeType.STORAGE, "s:m r:badvalue");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            NodeState.deserialize(NodeType.STORAGE, "s:m i:badvalue");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            NodeState.deserialize(NodeType.STORAGE, "s:m t:badvalue");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            NodeState.deserialize(NodeType.STORAGE, "s:m t:-1");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            NodeState.deserialize(NodeType.STORAGE, "s:m d:badvalue");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            NodeState.deserialize(NodeType.STORAGE, "s:m d.badkey:badvalue");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            NodeState.deserialize(NodeType.STORAGE, "s:m d.1:badindex");
            assertTrue("Should fail", false);
        } catch (Exception e) {}

        ns = new NodeState(NodeType.STORAGE, State.UP).setDescription("Foo bar");
        assertEquals("", ns.serialize(2, false));
        assertEquals("m:Foo\\x20bar", ns.serialize(false));
        assertEquals("m:Foo\\x20bar", ns.serialize(true));

        ns = new NodeState(NodeType.STORAGE, State.MAINTENANCE).setDescription("Foo bar").setCapacity(1.2).setDiskCount(4)
                .setMinUsedBits(12).setStartTimestamp(5).setDiskState(1, new DiskState(State.DOWN, "bad disk", 1))
                .setDiskState(3, new DiskState(State.UP, "", 2));
        assertEquals(".2.s:m .2.c:1.2 .2.t:5 .2.d:4 .2.d.1.s:d .2.d.3.c:2.0", ns.serialize(2, false));
        assertEquals("s:m c:1.2 t:5 b:12 d:4 d.1.s:d d.3.c:2.0 m:Foo\\x20bar", ns.serialize(false));
        assertEquals("s:m c:1.2 t:5 b:12 d:4 d.1.s:d d.1.m:bad\\x20disk d.3.c:2.0 m:Foo\\x20bar", ns.serialize(true));
        NodeState ns2 = NodeState.deserialize(NodeType.STORAGE, "s:m c:1.2 t:5 b:12 d:4 d.1.s:d d.1.m:bad\\x20disk d.3.c:2.0 m:Foo\\x20bar");
        assertEquals(ns, ns2);

        NodeState copy1 = NodeState.deserialize(NodeType.STORAGE, ns.serialize(false));
        NodeState copy2 = NodeState.deserialize(NodeType.STORAGE, ns.serialize(true));
        assertEquals(ns, copy1);
        assertEquals(ns, copy2);
        assertEquals(copy1, copy2);
        assertEquals(ns.serialize(false), copy1.serialize(false));
        assertEquals(ns.serialize(false), copy2.serialize(false));
        assertEquals(ns.serialize(true), copy2.serialize(true));
    }

    @Test
    public void testSimilarTo() {
        {
            NodeState ns1 = new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0);
            NodeState ns2 = new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(NodeState.getListingBucketsInitProgressLimit() / 2);
            NodeState ns3 = new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(NodeState.getListingBucketsInitProgressLimit());
            NodeState ns4 = new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(NodeState.getListingBucketsInitProgressLimit() * 2);
            assertTrue(ns1.similarTo(ns2));
            assertFalse(ns2.similarTo(ns3));
            assertTrue(ns3.similarTo(ns4));

            assertTrue(ns1.similarToIgnoringInitProgress(ns2));
            assertTrue(ns1.similarToIgnoringInitProgress(ns3));
            assertTrue(ns3.similarToIgnoringInitProgress(ns1));
            assertTrue(ns1.similarToIgnoringInitProgress(ns4));
            assertTrue(ns2.similarToIgnoringInitProgress(ns4));

            assertFalse(ns1.equals(ns2));
            assertFalse(ns2.equals(ns3));
            assertFalse(ns3.equals(ns4));

            assertFalse(ns1.equals("class not instance of NodeState"));
            assertFalse(ns1.similarTo("class not instance of NodeState"));
        }
        {
            NodeState ns1 = new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(16);
            NodeState ns2 = new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(18);
            assertTrue(ns1.similarTo(ns2));
            assertTrue(ns1.similarToIgnoringInitProgress(ns2));
            assertFalse(ns1.equals(ns2));
        }
        {
            NodeState ns = new NodeState(NodeType.STORAGE, State.MAINTENANCE);
            NodeState ns2Disks = new NodeState(NodeType.STORAGE, State.MAINTENANCE).setDiskCount(2);
            assertEquals(ns, ns2Disks);
            assertEquals(ns2Disks, ns);
            assertTrue(ns.similarTo(ns2Disks));
            assertTrue(ns.similarToIgnoringInitProgress(ns2Disks));
            assertTrue(ns2Disks.similarTo(ns));

            ns2Disks.getDiskState(0).setState(State.DOWN);
            assertFalse(ns.equals(ns2Disks));
            assertFalse(ns2Disks.equals(ns));
            assertFalse(ns.similarTo(ns2Disks));
            assertFalse(ns.similarToIgnoringInitProgress(ns2Disks));
            assertFalse(ns2Disks.similarTo(ns));
        }
    }

    @Test
    public void testReadableOutput() {
        // toString() and getDiff() is mostly there just to make good error reports when unit tests fails. Make sure toString() is actually run with no test failures
        // to make sure coverage doesn't complain when no test is failing.
        NodeState ns = new NodeState(NodeType.STORAGE, State.MAINTENANCE);
        String expected = "Maintenance => Up";
        assertEquals(expected, ns.getTextualDifference(new NodeState(NodeType.STORAGE, State.UP)).substring(0, expected.length()));

        NodeState ns1 = new NodeState(NodeType.STORAGE, State.MAINTENANCE).setDescription("Foo bar").setCapacity(1.2).setReliability(2).setDiskCount(4)
                .setMinUsedBits(12).setStartTimestamp(5).setDiskState(1, new DiskState(State.DOWN, "bad disk", 1))
                .setDiskState(3, new DiskState(State.UP, "", 2));
        ns1.toString();
        ns1.toString(true);
        expected = "Maintenance => Up, capacity: 1.2 => 1.0, reliability: 2 => 1, minUsedBits: 12 => 16, startTimestamp: 5 => 0, disks: 4 => 0, description: Foo bar => ";
        assertEquals(expected, ns1.getTextualDifference(new NodeState(NodeType.STORAGE, State.UP)).substring(0, expected.length()));
    }

    @Test
    public void testValidInClusterState() {
        try{
            new NodeState(NodeType.DISTRIBUTOR, State.UNKNOWN).verifyValidInSystemState(NodeType.DISTRIBUTOR);
            assertTrue("Should not be valid", false);
        } catch (Exception e) {}
        try{
            new NodeState(NodeType.DISTRIBUTOR, State.UP).setCapacity(3).verifyValidInSystemState(NodeType.DISTRIBUTOR);
            assertTrue("Should not be valid", false);
        } catch (Exception e) {}
        try{
            new NodeState(NodeType.DISTRIBUTOR, State.UP).setReliability(3).verifyValidInSystemState(NodeType.DISTRIBUTOR);
            assertTrue("Should not be valid", false);
        } catch (Exception e) {}
        try{
            new NodeState(NodeType.DISTRIBUTOR, State.UP).setDiskCount(2).verifyValidInSystemState(NodeType.DISTRIBUTOR);
            assertTrue("Should not be valid", false);
        } catch (Exception e) {}
    }

}
