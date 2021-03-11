// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.state;

import org.junit.Test;

import java.text.ParseException;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClusterStateTestCase{

    @Test
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
        state.setNodeState(new Node(NodeType.STORAGE, 0), new NodeState(NodeType.STORAGE, State.UP));
        assertEquals("distributor:5 .0.s:d .2.s:d .3.s:d storage:1", state.toString());
        state.setNodeState(new Node(NodeType.STORAGE, 0), new NodeState(NodeType.STORAGE, State.DOWN));
        assertEquals("distributor:5 .0.s:d .2.s:d .3.s:d", state.toString());
    }

    @Test
    public void testClone() throws ParseException {
        ClusterState state = new ClusterState("");
        state.setNodeState(new Node(NodeType.DISTRIBUTOR, 1), new NodeState(NodeType.DISTRIBUTOR, State.UP).setDescription("available"));
        state.setNodeState(new Node(NodeType.STORAGE, 0), new NodeState(NodeType.STORAGE, State.UP).setCapacity(1.2f));
        state.setNodeState(new Node(NodeType.STORAGE, 2), new NodeState(NodeType.STORAGE, State.UP));
        ClusterState other = state.clone();
        assertEquals(state.toString(true), other.toString(true));
        assertEquals(state.toString(false), other.toString(false));
        assertEquals(state, other);
    }

    @Test
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
            assertFalse(state1.similarToIgnoringInitProgress(state2));
        }

        {
            ClusterState state1 = new ClusterState("cluster:d");
            ClusterState state2 = new ClusterState("cluster:d version:1 bits:20 distributor:1 storage:1 .0.s:d");
            assertFalse(state1.equals(state2));
            assertTrue(state1.similarTo(state2));
            assertTrue(state1.similarToIgnoringInitProgress(state2));
        }

        {
            ClusterState state1 = new ClusterState("distributor:3 .1.s:d .2.s:m storage:3 .1.s:i .2.s:r");
            ClusterState state2 = new ClusterState("distributor:3 storage:3");
            assertFalse(state1.equals(state2));
            assertFalse(state1.similarTo(state2));
            assertFalse(state1.similarToIgnoringInitProgress(state2));
        }

        assertFalse(state.equals("class not instance of ClusterState"));
        assertFalse(state.similarTo("class not instance of ClusterState"));

        assertEquals(state, state);
        assertTrue(state.similarTo(state));
    }

    private static ClusterState stateFromString(final String stateStr) {
        try {
            return new ClusterState(stateStr);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private void do_test_differing_storage_node_sets(BiFunction<ClusterState, ClusterState, Boolean> cmp) {
        final ClusterState a = stateFromString("distributor:3 storage:3 .0.s:d");
        final ClusterState b = stateFromString("distributor:3 storage:3");
        assertFalse(cmp.apply(a, b));
        assertFalse(cmp.apply(b, a));
        assertTrue(cmp.apply(a, a));
        assertTrue(cmp.apply(b, b));
    }

    private void do_test_differing_distributor_node_sets(BiFunction<ClusterState, ClusterState, Boolean> cmp) {
        final ClusterState a = stateFromString("distributor:3 .0.s:d storage:3");
        final ClusterState b = stateFromString("distributor:3 storage:3");
        assertFalse(cmp.apply(a, b));
        assertFalse(cmp.apply(b, a));
        assertTrue(cmp.apply(a, a));
        assertTrue(cmp.apply(b, b));
    }

    @Test
    public void similarity_check_considers_differing_distributor_node_state_sets() {
        do_test_differing_distributor_node_sets((a, b) -> a.similarTo(b));
    }

    @Test
    public void similarity_check_considers_differing_storage_node_state_sets() {
        do_test_differing_storage_node_sets((a, b) -> a.similarTo(b));
    }

    @Test
    public void structural_similarity_check_considers_differing_distributor_node_state_sets() {
        do_test_differing_distributor_node_sets((a, b) -> a.similarToIgnoringInitProgress(b));
    }

    @Test
    public void init_progress_ignoring_similarity_check_considers_differing_storage_node_state_sets() {
        do_test_differing_storage_node_sets((a, b) -> a.similarToIgnoringInitProgress(b));
    }

    private void do_test_similarity_for_down_cluster_state(BiFunction<ClusterState, ClusterState, Boolean> cmp) {
        final ClusterState a = stateFromString("cluster:d distributor:3 .0.s:d storage:3 .2:s:d");
        final ClusterState b = stateFromString("cluster:d distributor:3 storage:3 .1:s:d");
        assertTrue(cmp.apply(a, b));
        assertTrue(cmp.apply(b, a));
    }

    @Test
    public void similarity_check_considers_differing_down_cluster_states_similar() {
        do_test_similarity_for_down_cluster_state((a, b) -> a.similarTo(b));
    }

    @Test
    public void init_progress_ignoring__similarity_check_considers_differing_down_cluster_states_similar() {
        do_test_similarity_for_down_cluster_state((a, b) -> a.similarToIgnoringInitProgress(b));
    }

    // If we naively only look at the NodeState sets in the ClusterState instances to be
    // compared, we might get false positives. If state A has a NodeState(Up, minBits 15)
    // while state B has NodeState(Up, minBits 16), the latter will be pruned away from the
    // NodeState set because it's got a "default" Up state. The two states are still semantically
    // similar, and should be returned as such. But their state sets technically differ.
    @Test
    public void similarity_check_does_not_consider_per_storage_node_min_bits() {
        final ClusterState a = stateFromString("distributor:4 storage:4");
        final ClusterState b = stateFromString("distributor:4 storage:4");
        b.setNodeState(new Node(NodeType.STORAGE, 1), new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(15));
        assertTrue(a.similarTo(b));
        assertTrue(b.similarTo(a));
    }

    @Test
    public void init_progress_ignoring_similarity_check_does_in_fact_ignore_init_progress() {
        final ClusterState a = stateFromString("distributor:3 storage:3 .0.i:0.01 .1.i:0.1 .2.i:0.9");
        final ClusterState b = stateFromString("distributor:3 storage:3 .0.i:0.2 .1.i:0.5 .2.i:0.99");
        assertTrue(a.similarToIgnoringInitProgress(b));
        assertTrue(b.similarToIgnoringInitProgress(a));
    }

    @Test
    public void testTextDiff() throws ParseException {
        ClusterState state1 = new ClusterState("distributor:9 storage:4");
        ClusterState state2 = new ClusterState("distributor:7 storage:6");
        ClusterState state3 = new ClusterState("distributor:9 storage:2");

        assertEquals("storage: [4: Down => Up, 5: Down => Up], distributor: [7: Up => Down, 8: Up => Down]", state1.getTextualDifference(state2));
        assertEquals("storage: [2: Up => Down, 3: Up => Down, 4: Up => Down, 5: Up => Down], distributor: [7: Down => Up, 8: Down => Up]", state2.getTextualDifference(state3));

        state2.setDistributionBits(21);
        state1.setVersion(123);
        state1.setNodeState(new Node(NodeType.STORAGE, 2), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.2f).setDescription("Booting"));

        assertEquals("version: 123 => 0, bits: 16 => 21, storage: [2: [Initializing => Up, description: Booting => ], 4: Down => Up, 5: Down => Up], distributor: [7: Up => Down, 8: Up => Down]", state1.getTextualDifference(state2));
    }

    @Test
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
        state1.setNodeState(new Node(NodeType.STORAGE, 2), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.2f).setDescription("Booting"));
        state2.setDistributionBits(21);
        assertEquals("version: 123 => 0, bits: 16 => 21, storage: [2: [Initializing => Up, description: Booting => ], 4: Down => Up, 5: Down => Up], distributor: [7: Up => Down, 8: Up => Down]", state1.getTextualDifference(state2));
        assertEquals("version: 123 =&gt; 0, bits: 16 =&gt; 21, storage: [<br>\n" +
                "&nbsp;2: [<b>Initializing</b> =&gt; <b>Up</b>, description: Booting =&gt; ], <br>\n" +
                "&nbsp;4: <b>Down</b> =&gt; <b>Up</b>, <br>\n" +
                "&nbsp;5: <b>Down</b> =&gt; <b>Up</b><br>\n" +
                "], distributor: [<br>\n" +
                "&nbsp;7: <b>Up</b> =&gt; <b>Down</b>, <br>\n" +
                "&nbsp;8: <b>Up</b> =&gt; <b>Down</b><br>\n" +
                "]", state1.getHtmlDifference(state2));
    }

    @Test
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

    @Test
    public void testCapacityExponential() throws ParseException {

        ClusterState state = new ClusterState("distributor:27 storage:170 .2.s:d .13.c:3E-8 .13.s:r");
        assertEquals(3E-8, state.getNodeState(new Node(NodeType.STORAGE, 13)).getCapacity(), 1E-8);
    }

    @Test
    public void testCapacityExponentialCpp() throws ParseException {
        ClusterState state = new ClusterState("distributor:27 storage:170 .2.s:d .13.c:3e-08 .13.s:r");
        assertEquals(3E-8, state.getNodeState(new Node(NodeType.STORAGE, 13)).getCapacity(), 1E-8);
    }

    @Test
    public void testSetState() throws ParseException {
        ClusterState state = new ClusterState("distributor:2 storage:2");
        state.setNodeState(new Node(NodeType.DISTRIBUTOR, 0), new NodeState(NodeType.DISTRIBUTOR, State.DOWN));

        assertEquals("distributor:2 .0.s:d storage:2", state.toString());
    }

    @Test
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

    @Test
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

    @Test
    public void testWhitespace() throws ParseException {
        ClusterState state = new ClusterState("distributor:2\n  .1.t:3\nstorage:2\n\t.0.s:i   \r\f.1.s:m");
        assertEquals(2, state.getNodeCount(NodeType.DISTRIBUTOR));
        assertEquals(2, state.getNodeCount(NodeType.STORAGE));
        assertEquals(new NodeState(NodeType.DISTRIBUTOR, State.UP), state.getNodeState(new Node(NodeType.DISTRIBUTOR, 0)));
        assertEquals(new NodeState(NodeType.DISTRIBUTOR, State.UP).setStartTimestamp(3), state.getNodeState(new Node(NodeType.DISTRIBUTOR, 1)));
        assertEquals(new NodeState(NodeType.STORAGE, State.INITIALIZING), state.getNodeState(new Node(NodeType.STORAGE, 0)));
        assertEquals(new NodeState(NodeType.STORAGE, State.MAINTENANCE), state.getNodeState(new Node(NodeType.STORAGE, 1)));
    }

    @Test
    public void empty_state_factory_method_returns_empty_state() {
        final ClusterState state = ClusterState.emptyState();
        assertEquals("", state.toString());
    }

    @Test
    public void state_from_string_factory_method_returns_cluster_state_constructed_from_input() {
        final String stateStr = "version:123 distributor:2 storage:2";
        final ClusterState state = ClusterState.stateFromString(stateStr);
        assertEquals(stateStr, state.toString());
    }

    @Test(expected=RuntimeException.class)
    public void state_from_string_factory_method_throws_runtime_exception_on_parse_failure() {
        ClusterState.stateFromString("fraggle rock");
    }
}
