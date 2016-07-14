package com.yahoo.vespa.clustercontroller.core;// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import static com.yahoo.vespa.clustercontroller.core.matchers.EventForNode.eventForNode;
import static com.yahoo.vespa.clustercontroller.core.matchers.NodeEventWithDescription.nodeEventWithDescription;
import static com.yahoo.vespa.clustercontroller.core.matchers.ClusterEventWithDescription.clusterEventWithDescription;
import static org.hamcrest.CoreMatchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.hasItem;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.clustercontroller.core.AnnotatedClusterState;
import com.yahoo.vespa.clustercontroller.core.ClusterStateReason;
import com.yahoo.vespa.clustercontroller.core.Event;
import com.yahoo.vespa.clustercontroller.core.EventDiffCalculator;
import com.yahoo.vespa.clustercontroller.core.NodeStateReason;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EventDiffCalculatorTest {

    // TODO de-dupe this functionality; used in several places both with and without state string input
    private static ClusterState clusterState(String stateStr) {
        try {
            return new ClusterState(stateStr);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // TODO de-dupe
    private static Node storageNode(int index) {
        return new Node(NodeType.STORAGE, index);
    }

    private static Node distributorNode(int index) {
        return new Node(NodeType.DISTRIBUTOR, index);
    }

    private static ClusterStateReason emptyClusterStateReason() {
        return null; // FIXME likely to change this, null is bull
    }

    private static Map<Node, NodeStateReason> emptyNodeStateReasons() {
        return Collections.emptyMap();
    }

    private static class EventFixture {
        final ClusterFixture clusterFixture;
        ClusterStateReason clusterReasonBefore = emptyClusterStateReason();
        ClusterStateReason clusterReasonAfter = emptyClusterStateReason();
        Map<Node, NodeStateReason> nodeReasonsBefore = emptyNodeStateReasons();
        Map<Node, NodeStateReason> nodeReasonsAfter = emptyNodeStateReasons();
        ClusterState clusterStateBefore = clusterState("");
        ClusterState clusterStateAfter = clusterState("");

        EventFixture(int nodeCount) {
            this.clusterFixture = ClusterFixture.forFlatCluster(nodeCount);
        }

        EventFixture clusterStateBefore(String stateStr) {
            clusterStateBefore = clusterState(stateStr);
            return this;
        }
        EventFixture clusterStateAfter(String stateStr) {
            clusterStateAfter = clusterState(stateStr);
            return this;
        }

        List<Event> computeEventDiff() {
            final AnnotatedClusterState stateBefore = new AnnotatedClusterState(
                    clusterStateBefore, clusterReasonBefore, nodeReasonsBefore);
            final AnnotatedClusterState stateAfter = new AnnotatedClusterState(
                    clusterStateAfter, clusterReasonAfter, nodeReasonsAfter);

            return EventDiffCalculator.computeEventDiff(
                    EventDiffCalculator.params()
                            .cluster(clusterFixture.cluster())
                            .previousClusterState(stateBefore)
                            .currentClusterState(stateAfter));
        }

        static EventFixture createForNodes(int nodeCount) {
            return new EventFixture(nodeCount);
        }

    }

    @Test
    public void single_storage_node_state_transition_emits_altered_node_state_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("distributor:3 storage:3 .0.s:d");

        final List<Event> events = fixture.computeEventDiff();
        assertEquals(events.size(), 1);
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(0)),
                nodeEventWithDescription("Altered node state in cluster from 'U' to 'D'"))));
    }

    @Test
    public void single_distributor_node_state_transition_emits_altered_node_state_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("distributor:3 .1.s:d storage:3");

        final List<Event> events = fixture.computeEventDiff();
        assertEquals(events.size(), 1);
        assertThat(events, hasItem(allOf(
                eventForNode(distributorNode(1)),
                nodeEventWithDescription("Altered node state in cluster from 'U' to 'D'"))));
    }

    @Test
    public void no_emitted_node_state_event_when_node_state_not_changed() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("distributor:3 storage:3");

        final List<Event> events = fixture.computeEventDiff();
        assertEquals(events.size(), 0);
    }

    @Test
    public void cluster_up_edge_emits_sufficient_node_availaiblity_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("cluster:d distributor:3 storage:3")
                .clusterStateAfter("distributor:3 storage:3");

        final List<Event> events = fixture.computeEventDiff();
        assertEquals(events.size(), 1);
        assertThat(events, hasItem(
                clusterEventWithDescription("Enough nodes available for system to become up")));
    }

    // TODO test type of event (CURRENT vs REPORTED etc)
    // TODO test and handle that events are created with correct time!
}
