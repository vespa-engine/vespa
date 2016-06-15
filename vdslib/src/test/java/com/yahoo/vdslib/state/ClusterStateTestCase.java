// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.state;

import java.text.ParseException;

public class ClusterStateTestCase extends junit.framework.TestCase {

    public void testSetNodeState() throws ParseException {
        ClusterState state = new ClusterState("");
        assertEquals("", state.toString());
        state.setNodeState(new Node(NodeType.DISTRIBUTOR, 3), new NodeState(NodeType.DISTRIBUTOR, State.UP));
        assertEquals("distributor:4 .0.s:d .1.s:d .2.s:d", state.toString());
        state.setNodeState(new Node(NodeType.DISTRIBUTOR, 1), new NodeState(NodeType.DISTRIBUTOR, State.UP));
        assertEquals("distributor:4 .0.s:d .2.s:d", state.toString());
        state.setNodeState(new Node(NodeType.DISTRIBUTOR, 3), new NodeState(NodeType.DISTRIBUTOR, State.DOWN));
        assertEquals("distributor:2 .0.s:d", state.toString());
        state.setNodeState(new Node(NodeType.DISTRIBUTOR, 4), new NodeState(NodeType.DISTRIBUTOR, State.UP));
        assertEquals("distributor:5 .0.s:d .2.s:d .3.s:d", state.toString());
        state.setNodeState(new Node(NodeType.STORAGE, 0), new NodeState(NodeType.STORAGE, State.UP).setDiskCount(4));
        assertEquals("distributor:5 .0.s:d .2.s:d .3.s:d storage:1", state.toString());
        state.setNodeState(new Node(NodeType.STORAGE, 0), new NodeState(NodeType.STORAGE, State.UP).setDiskCount(4).setDiskState(1, new DiskState(State.DOWN)));
        assertEquals("distributor:5 .0.s:d .2.s:d .3.s:d storage:1 .0.d:4 .0.d.1.s:d", state.toString());
    }

    public void testClone() throws ParseException {
        ClusterState state = new ClusterState("");
        state.setNodeState(new Node(NodeType.DISTRIBUTOR, 1), new NodeState(NodeType.DISTRIBUTOR, State.UP).setDescription("available"));
        state.setNodeState(new Node(NodeType.STORAGE, 0), new NodeState(NodeType.STORAGE, State.UP).setCapacity(1.2).setReliability(2));
        state.setNodeState(new Node(NodeType.STORAGE, 2), new NodeState(NodeType.STORAGE, State.UP).setDiskCount(2).setDiskState(1, new DiskState(State.DOWN)));
        ClusterState other = state.clone();
        assertEquals(state.toString(true), other.toString(true));
        assertEquals(state.toString(false), other.toString(false));
        assertEquals(state, other);
   }

    public void testEquals() throws ParseException {
        ClusterState state = new ClusterState("");

        assertEquals(state, new ClusterState(""));

        assertEquals(state, new ClusterState("version:0"));
        assertEquals(state, new ClusterState("cluster:u"));
        assertEquals(state, new ClusterState("bits:16"));
        assertEquals(state, new ClusterState("storage:0"));
        assertEquals(state, new ClusterState("distributor:0"));

        assertFalse(state.equals(new ClusterState("version:1")));
        assertFalse(state.equals(new ClusterState("cluster:d")));
        assertFalse(state.equals(new ClusterState("bits:20")));
        assertFalse(state.equals(new ClusterState("storage:1")));
        assertFalse(state.equals(new ClusterState("distributor:1")));

        {
            ClusterState state1 = new ClusterState("distributor:3 .1.s:d .2.s:m storage:3 .1.s:i .2.s:r");
            ClusterState state2 = new ClusterState("distributor:3 .1.s:d .2.s:m storage:3 .1.s:i .2.s:m");
            assertFalse(state1.equals(state2));
            assertFalse(state1.similarTo(state2));
        }

        {
            ClusterState state1 = new ClusterState("cluster:d");
            ClusterState state2 = new ClusterState("cluster:d version:1 bits:20 distributor:1 storage:1 .0.s:d");
            assertFalse(state1.equals(state2));
            assertTrue(state1.similarTo(state2));
        }

        {
            ClusterState state1 = new ClusterState("distributor:3 .1.s:d .2.s:m storage:3 .1.s:i .2.s:r");
            ClusterState state2 = new ClusterState("distributor:3 storage:3");
            assertFalse(state1.equals(state2));
            assertFalse(state1.similarTo(state2));
        }

        assertFalse(state.equals("class not instance of ClusterState"));
        assertFalse(state.similarTo("class not instance of ClusterState"));

        assertEquals(state, state);
        assertTrue(state.similarTo(state));
    }

    public void testTextDiff() throws ParseException {
        ClusterState state1 = new ClusterState("distributor:9 storage:4");
        ClusterState state2 = new ClusterState("distributor:7 storage:6");
        ClusterState state3 = new ClusterState("distributor:9 storage:2");

        assertEquals("storage: [4: Down => Up, 5: Down => Up], distributor: [7: Up => Down, 8: Up => Down]", state1.getTextualDifference(state2));
        assertEquals("storage: [2: Up => Down, 3: Up => Down, 4: Up => Down, 5: Up => Down], distributor: [7: Down => Up, 8: Down => Up]", state2.getTextualDifference(state3));

        state2.setDistributionBits(21);
        state1.setVersion(123);
        state1.setNodeState(new Node(NodeType.STORAGE, 2), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.2).setDiskCount(2).setDescription("Booting"));
        state2.setOfficial(true);

        assertEquals("version: 123 => 0, bits: 16 => 21, official: false => true, storage: [2: [Initializing => Up, disks: 2 => 0, description: Booting => ], 4: Down => Up, 5: Down => Up], distributor: [7: Up => Down, 8: Up => Down]", state1.getTextualDifference(state2));
    }

    public void testHtmlDiff() throws ParseException {
        ClusterState state1 = new ClusterState("distributor:9 storage:4");
        ClusterState state2 = new ClusterState("distributor:7 storage:6");
        ClusterState state3 = new ClusterState("distributor:9 storage:2");

        assertEquals("storage: [4: Down => Up, 5: Down => Up], distributor: [7: Up => Down, 8: Up => Down]", state1.getTextualDifference(state2));
        assertEquals("storage: [<br>\n" +
                "&nbsp;4: <b>Down</b> =&gt; <b>Up</b>, <br>\n" +
                "&nbsp;5: <b>Down</b> =&gt; <b>Up</b><br>\n" +
                "], distributor: [<br>\n" +
                "&nbsp;7: <b>Up</b> =&gt; <b>Down</b>, <br>\n" +
                "&nbsp;8: <b>Up</b> =&gt; <b>Down</b><br>\n" +
                "]", state1.getHtmlDifference(state2));
        assertEquals("storage: [2: Up => Down, 3: Up => Down, 4: Up => Down, 5: Up => Down], distributor: [7: Down => Up, 8: Down => Up]", state2.getTextualDifference(state3));
        assertEquals("storage: [<br>\n" +
                "&nbsp;2: <b>Up</b> =&gt; <b>Down</b>, <br>\n" +
                "&nbsp;3: <b>Up</b> =&gt; <b>Down</b>, <br>\n" +
                "&nbsp;4: <b>Up</b> =&gt; <b>Down</b>, <br>\n" +
                "&nbsp;5: <b>Up</b> =&gt; <b>Down</b><br>\n" +
                "], distributor: [<br>\n" +
                "&nbsp;7: <b>Down</b> =&gt; <b>Up</b>, <br>\n" +
                "&nbsp;8: <b>Down</b> =&gt; <b>Up</b><br>\n" +
                "]", state2.getHtmlDifference(state3));

        state1.setVersion(123);
        state1.setNodeState(new Node(NodeType.STORAGE, 2), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.2).setDiskCount(2).setDescription("Booting"));
        state2.setDistributionBits(21);
        state2.setOfficial(true);
        assertEquals("version: 123 => 0, bits: 16 => 21, official: false => true, storage: [2: [Initializing => Up, disks: 2 => 0, description: Booting => ], 4: Down => Up, 5: Down => Up], distributor: [7: Up => Down, 8: Up => Down]", state1.getTextualDifference(state2));
        assertEquals("version: 123 =&gt; 0, bits: 16 =&gt; 21, official: false =&gt; true, storage: [<br>\n" +
                "&nbsp;2: [<b>Initializing</b> =&gt; <b>Up</b>, disks: 2 =&gt; 0, description: Booting =&gt; ], <br>\n" +
                "&nbsp;4: <b>Down</b> =&gt; <b>Up</b>, <br>\n" +
                "&nbsp;5: <b>Down</b> =&gt; <b>Up</b><br>\n" +
                "], distributor: [<br>\n" +
                "&nbsp;7: <b>Up</b> =&gt; <b>Down</b>, <br>\n" +
                "&nbsp;8: <b>Up</b> =&gt; <b>Down</b><br>\n" +
                "]", state1.getHtmlDifference(state2));
    }


    public void testParser() throws ParseException {
        ClusterState state = new ClusterState("distributor:2 storage:17 .2.s:d .13.s:r m:cluster\\x20message");
        assertEquals("cluster message", state.getDescription());

        for (int i = 0; i < state.getNodeCount(NodeType.STORAGE); i++) {
            if (i == 2)
                assertEquals("d", state.getNodeState(new Node(NodeType.STORAGE, i)).getState().serialize());
            if (i == 13)
                assertEquals("r", state.getNodeState(new Node(NodeType.STORAGE, i)).getState().serialize());
            if (i != 2 && i != 13)
                assertEquals("u", state.getNodeState(new Node(NodeType.STORAGE, i)).getState().serialize());
        }

        assertEquals("distributor:2 storage:17 .2.s:d .13.s:r", state.toString());
        assertEquals("distributor:2", new ClusterState("distributor:2 storage:0").toString());
        assertEquals("storage:2", new ClusterState("storage:2 .0.d:3 .1.d:4").toString());
        assertEquals("distributor:2 .1.t:3 storage:2", new ClusterState("whatever:4 distributor:2 .1.t:3 storage:2").toString());
        assertEquals("distributor:2 storage:2", new ClusterState(": distributor:2 storage:2 .0:d cbadkey:u bbadkey:2 vbadkey:2 mbadkey:message dbadkey:5 sbadkey:6 unknownkey:somevalue").toString());

        try {
            new ClusterState("badtokenwithoutcolon");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            new ClusterState(".0.s:d");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            new ClusterState("cluster:badvalue");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            new ClusterState("cluster:m");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            new ClusterState("version:badvalue");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            new ClusterState("distributor:badvalue");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            new ClusterState("storage:badvalue");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            new ClusterState("distributor:2 .3.s:d");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
        try {
            new ClusterState("storage:2 .3.s:d");
            assertTrue("Should fail", false);
        } catch (Exception e) {}
    }

    public void testCapacityExponential() throws ParseException {

        ClusterState state = new ClusterState("distributor:27 storage:170 .2.s:d .13.c:3E-8 .13.s:r");
        assertEquals(3E-8, state.getNodeState(new Node(NodeType.STORAGE, 13)).getCapacity());
    }

    public void testCapacityExponentialCpp() throws ParseException {
        ClusterState state = new ClusterState("distributor:27 storage:170 .2.s:d .13.c:3e-08 .13.s:r");
        assertEquals(3E-8, state.getNodeState(new Node(NodeType.STORAGE, 13)).getCapacity());
    }

    public void testSetState() throws ParseException {
        ClusterState state = new ClusterState("distributor:2 storage:2");
        state.setNodeState(new Node(NodeType.DISTRIBUTOR, 0), new NodeState(NodeType.DISTRIBUTOR, State.DOWN));

        assertEquals("distributor:2 .0.s:d storage:2", state.toString());
    }

    public void testVersionAndClusterStates() throws ParseException {
        ClusterState state = new ClusterState("version:4 cluster:i distributor:2 .1.s:i storage:2 .0.s:i .0.i:0.345");
        assertEquals(4, state.getVersion());
        assertEquals(State.INITIALIZING, state.getClusterState());
        assertEquals(0.345, state.getNodeState(new Node(NodeType.STORAGE, 0)).getInitProgress(), 0.000001);
        state.setClusterState(State.DOWN);
        state.setVersion(5);
        state.setDistributionBits(12);
        assertEquals("version:5 cluster:d bits:12 distributor:2 .1.s:i .1.i:1.0 storage:2 .0.s:i .0.i:0.345", state.toString());
    }

    public void testNotRemovingCommentedDownNodesAtEnd() throws ParseException {
        ClusterState state = new ClusterState("");
        state.setNodeState(new Node(NodeType.DISTRIBUTOR, 0), new NodeState(NodeType.DISTRIBUTOR, State.UP));
        state.setNodeState(new Node(NodeType.DISTRIBUTOR, 1), new NodeState(NodeType.DISTRIBUTOR, State.UP));
        state.setNodeState(new Node(NodeType.STORAGE, 0), new NodeState(NodeType.STORAGE, State.UP));
        state.setNodeState(new Node(NodeType.STORAGE, 1), new NodeState(NodeType.STORAGE, State.UP));
        state.setNodeState(new Node(NodeType.STORAGE, 2), new NodeState(NodeType.STORAGE, State.UP));
        assertEquals("distributor:2 storage:3", state.toString());
        state.setNodeState(new Node(NodeType.STORAGE, 2), new NodeState(NodeType.STORAGE, State.DOWN).setDescription("Took it down"));
        state.setNodeState(new Node(NodeType.DISTRIBUTOR, 1), new NodeState(NodeType.DISTRIBUTOR, State.DOWN).setDescription("Took it down"));
        assertEquals("distributor:2 .1.s:d .1.m:Took\\x20it\\x20down storage:3 .2.s:d .2.m:Took\\x20it\\x20down", state.toString(true));
        assertEquals("distributor:1 storage:2", state.toString(false));
    }

    public void testWhitespace() throws ParseException {
        ClusterState state = new ClusterState("distributor:2\n  .1.t:3\nstorage:2\n\t.0.s:i   \r\f.1.s:m");
        assertEquals(2, state.getNodeCount(NodeType.DISTRIBUTOR));
        assertEquals(2, state.getNodeCount(NodeType.STORAGE));
        assertEquals(new NodeState(NodeType.DISTRIBUTOR, State.UP), state.getNodeState(new Node(NodeType.DISTRIBUTOR, 0)));
        assertEquals(new NodeState(NodeType.DISTRIBUTOR, State.UP).setStartTimestamp(3), state.getNodeState(new Node(NodeType.DISTRIBUTOR, 1)));
        assertEquals(new NodeState(NodeType.STORAGE, State.INITIALIZING), state.getNodeState(new Node(NodeType.STORAGE, 0)));
        assertEquals(new NodeState(NodeType.STORAGE, State.MAINTENANCE), state.getNodeState(new Node(NodeType.STORAGE, 1)));
    }
}
