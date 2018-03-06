// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import static com.yahoo.vespa.clustercontroller.core.matchers.EventForNode.eventForNode;
import static com.yahoo.vespa.clustercontroller.core.matchers.NodeEventForBucketSpace.nodeEventForBucketSpace;
import static com.yahoo.vespa.clustercontroller.core.matchers.NodeEventForBucketSpace.nodeEventForBaseline;
import static com.yahoo.vespa.clustercontroller.core.matchers.NodeEventWithDescription.nodeEventWithDescription;
import static com.yahoo.vespa.clustercontroller.core.matchers.ClusterEventWithDescription.clusterEventWithDescription;
import static com.yahoo.vespa.clustercontroller.core.matchers.EventTypeIs.eventTypeIs;
import static com.yahoo.vespa.clustercontroller.core.matchers.EventTimeIs.eventTimeIs;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.hasItem;

import static com.yahoo.vespa.clustercontroller.core.ClusterFixture.storageNode;
import static com.yahoo.vespa.clustercontroller.core.ClusterFixture.distributorNode;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EventDiffCalculatorTest {

    private static class EventFixture {
        final ClusterFixture clusterFixture;
        AnnotatedClusterState.Builder baselineBefore = new AnnotatedClusterState.Builder();
        AnnotatedClusterState.Builder baselineAfter = new AnnotatedClusterState.Builder();
        Map<String, AnnotatedClusterState.Builder> derivedBefore = new HashMap<>();
        Map<String, AnnotatedClusterState.Builder> derivedAfter = new HashMap<>();
        long currentTimeMs = 0;

        EventFixture(int nodeCount) {
            this.clusterFixture = ClusterFixture.forFlatCluster(nodeCount);
        }

        EventFixture clusterStateBefore(String stateStr) {
            baselineBefore.clusterState(stateStr);
            return this;
        }
        EventFixture clusterStateAfter(String stateStr) {
            baselineAfter.clusterState(stateStr);
            return this;
        }
        EventFixture storageNodeReasonBefore(int nodeIndex, NodeStateReason reason) {
            baselineBefore.storageNodeReason(nodeIndex, reason);
            return this;
        }
        EventFixture storageNodeReasonAfter(int nodeIndex, NodeStateReason reason) {
            baselineAfter.storageNodeReason(nodeIndex, reason);
            return this;
        }
        EventFixture clusterReasonBefore(ClusterStateReason reason) {
            baselineBefore.clusterReason(reason);
            return this;
        }
        EventFixture clusterReasonAfter(ClusterStateReason reason) {
            baselineAfter.clusterReason(reason);
            return this;
        }
        EventFixture currentTimeMs(long timeMs) {
            this.currentTimeMs = timeMs;
            return this;
        }
        EventFixture derivedClusterStateBefore(String bucketSpace, String stateStr) {
            getBuilder(derivedBefore, bucketSpace).clusterState(stateStr);
            return this;
        }
        EventFixture derivedClusterStateAfter(String bucketSpace, String stateStr) {
            getBuilder(derivedAfter, bucketSpace).clusterState(stateStr);
            return this;
        }
        EventFixture derivedStorageNodeReasonBefore(String bucketSpace, int nodeIndex, NodeStateReason reason) {
            getBuilder(derivedBefore, bucketSpace).storageNodeReason(nodeIndex, reason);
            return this;
        }
        EventFixture derivedStorageNodeReasonAfter(String bucketSpace, int nodeIndex, NodeStateReason reason) {
            getBuilder(derivedAfter, bucketSpace).storageNodeReason(nodeIndex, reason);
            return this;
        }
        private static AnnotatedClusterState.Builder getBuilder(Map<String, AnnotatedClusterState.Builder> derivedStates, String bucketSpace) {
            AnnotatedClusterState.Builder result = derivedStates.get(bucketSpace);
            if (result == null) {
                result = new AnnotatedClusterState.Builder();
                derivedStates.put(bucketSpace, result);
            }
            return result;
        }

        List<Event> computeEventDiff() {
            return EventDiffCalculator.computeEventDiff(
                    EventDiffCalculator.params()
                            .cluster(clusterFixture.cluster())
                            .fromState(ClusterStateBundle.of(baselineBefore.build(), toDerivedStates(derivedBefore)))
                            .toState(ClusterStateBundle.of(baselineAfter.build(), toDerivedStates(derivedAfter)))
                            .currentTimeMs(currentTimeMs));
        }

        private static Map<String, AnnotatedClusterState> toDerivedStates(Map<String, AnnotatedClusterState.Builder> derivedBuilders) {
            return derivedBuilders.entrySet().stream()
                    .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue().build()));
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
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(0)),
                eventTypeIs(NodeEvent.Type.CURRENT),
                nodeEventWithDescription("Altered node state in cluster state from 'U' to 'D'"))));
    }

    @Test
    public void single_distributor_node_state_transition_emits_altered_node_state_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("distributor:3 .1.s:d storage:3");

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(allOf(
                eventForNode(distributorNode(1)),
                eventTypeIs(NodeEvent.Type.CURRENT),
                nodeEventWithDescription("Altered node state in cluster state from 'U' to 'D'"))));
    }

    @Test
    public void node_state_change_event_is_tagged_with_given_time() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("distributor:3 storage:3 .0.s:d")
                .currentTimeMs(123456);

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(eventTimeIs(123456)));
    }

    @Test
    public void multiple_node_state_transitions_emit_multiple_node_state_events() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3 .1.s:d")
                .clusterStateAfter("distributor:3 .2.s:d storage:3 .0.s:r");

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(3));
        assertThat(events, hasItem(allOf(
                eventForNode(distributorNode(2)),
                nodeEventWithDescription("Altered node state in cluster state from 'U' to 'D'"))));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(0)),
                nodeEventWithDescription("Altered node state in cluster state from 'U' to 'R'"))));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(1)),
                nodeEventWithDescription("Altered node state in cluster state from 'D' to 'U'"))));
    }

    @Test
    public void no_emitted_node_state_event_when_node_state_not_changed() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("distributor:3 storage:3");

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(0));
    }

    @Test
    public void node_down_edge_with_group_down_reason_has_separate_event_emitted() {
        // We sneakily use a flat cluster here but still use a 'group down' reason. Differ doesn't currently care.
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("distributor:3 storage:3 .1.s:d")
                .storageNodeReasonAfter(1, NodeStateReason.GROUP_IS_DOWN);

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(2));
        // Both the regular edge event and the group down event is emitted
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(1)),
                nodeEventWithDescription("Altered node state in cluster state from 'U' to 'D'"))));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(1)),
                eventTypeIs(NodeEvent.Type.CURRENT),
                nodeEventWithDescription("Group node availability is below configured threshold"))));
    }

    @Test
    public void group_down_to_group_down_does_not_emit_new_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3 .1.s:d")
                .clusterStateAfter("distributor:3 storage:3 .1.s:m")
                .storageNodeReasonBefore(1, NodeStateReason.GROUP_IS_DOWN)
                .storageNodeReasonAfter(1, NodeStateReason.GROUP_IS_DOWN);

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        // Should not get a group availability event since nothing has changed in this regard
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(1)),
                nodeEventWithDescription("Altered node state in cluster state from 'D' to 'M'"))));
    }

    @Test
    public void group_down_to_clear_reason_emits_group_up_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3 .2.s:d")
                .clusterStateAfter("distributor:3 storage:3")
                .storageNodeReasonBefore(2, NodeStateReason.GROUP_IS_DOWN); // But no after-reason.

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(2));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(2)),
                nodeEventWithDescription("Altered node state in cluster state from 'D' to 'U'"))));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(2)),
                eventTypeIs(NodeEvent.Type.CURRENT),
                nodeEventWithDescription("Group node availability has been restored"))));
    }

    @Test
    public void cluster_up_edge_emits_sufficient_node_availability_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("cluster:d distributor:3 storage:3")
                .clusterStateAfter("distributor:3 storage:3");

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(
                clusterEventWithDescription("Enough nodes available for system to become up")));
    }

    @Test
    public void cluster_down_event_without_reason_annotation_emits_generic_down_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("cluster:d distributor:3 storage:3");

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(
                clusterEventWithDescription("Cluster is down")));
    }

    @Test
    public void cluster_event_is_tagged_with_given_time() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("cluster:d distributor:3 storage:3")
                .currentTimeMs(56789);

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(eventTimeIs(56789)));
    }

    @Test
    public void no_event_emitted_for_cluster_down_to_down_edge() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("cluster:d distributor:3 storage:3")
                .clusterStateAfter("cluster:d distributor:3 storage:3");

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(0));
    }

    @Test
    public void too_few_storage_nodes_cluster_down_reason_emits_corresponding_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("cluster:d distributor:3 storage:3")
                .clusterReasonAfter(ClusterStateReason.TOO_FEW_STORAGE_NODES_AVAILABLE);

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        // TODO(?) these messages currently don't include the current configured limits
        assertThat(events, hasItem(
                clusterEventWithDescription("Too few storage nodes available in cluster. Setting cluster state down")));
    }

    @Test
    public void too_few_distributor_nodes_cluster_down_reason_emits_corresponding_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("cluster:d distributor:3 storage:3")
                .clusterReasonAfter(ClusterStateReason.TOO_FEW_DISTRIBUTOR_NODES_AVAILABLE);

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(
                clusterEventWithDescription("Too few distributor nodes available in cluster. Setting cluster state down")));
    }

    @Test
    public void too_low_storage_node_ratio_cluster_down_reason_emits_corresponding_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("cluster:d distributor:3 storage:3")
                .clusterReasonAfter(ClusterStateReason.TOO_LOW_AVAILABLE_STORAGE_NODE_RATIO);

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(
                clusterEventWithDescription("Too low ratio of available storage nodes. Setting cluster state down")));
    }

    @Test
    public void too_low_distributor_node_ratio_cluster_down_reason_emits_corresponding_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("cluster:d distributor:3 storage:3")
                .clusterReasonAfter(ClusterStateReason.TOO_LOW_AVAILABLE_DISTRIBUTOR_NODE_RATIO);

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(
                clusterEventWithDescription("Too low ratio of available distributor nodes. Setting cluster state down")));
    }

    @Test
    public void may_have_merges_pending_up_edge_event_emitted_if_derived_bucket_space_state_differs_from_baseline() {
        EventFixture f = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .derivedClusterStateBefore("default", "distributor:3 storage:3")
                .clusterStateAfter("distributor:3 storage:3")
                .derivedClusterStateAfter("default", "distributor:3 storage:3 .1.s:m")
                .derivedStorageNodeReasonAfter("default", 1, NodeStateReason.MAY_HAVE_MERGES_PENDING);

        List<Event> events = f.computeEventDiff();
        assertThat(events.size(), equalTo(2));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(1)),
                nodeEventForBucketSpace("default"),
                nodeEventWithDescription("Altered node state in cluster state from 'U' to 'M'"))));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(1)),
                nodeEventForBucketSpace("default"),
                nodeEventWithDescription("Node may have merges pending"))));
    }

    @Test
    public void may_have_merges_pending_down_edge_event_emitted_if_derived_bucket_space_state_differs_from_baseline() {
        EventFixture f = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .derivedClusterStateBefore("default", "distributor:3 storage:3 .1.s:m")
                .derivedStorageNodeReasonBefore("default", 1, NodeStateReason.MAY_HAVE_MERGES_PENDING)
                .clusterStateAfter("distributor:3 storage:3")
                .derivedClusterStateAfter("default", "distributor:3 storage:3");

        List<Event> events = f.computeEventDiff();
        assertThat(events.size(), equalTo(2));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(1)),
                nodeEventForBucketSpace("default"),
                nodeEventWithDescription("Altered node state in cluster state from 'M' to 'U'"))));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(1)),
                nodeEventForBucketSpace("default"),
                nodeEventWithDescription("Node no longer have merges pending"))));
    }

    @Test
    public void both_baseline_and_derived_bucket_space_state_events_are_emitted() {
        EventFixture f = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .derivedClusterStateBefore("default", "distributor:3 storage:3")
                .clusterStateAfter("distributor:3 storage:3 .0.s:m")
                .derivedClusterStateAfter("default", "distributor:3 storage:3 .1.s:m");

        List<Event> events = f.computeEventDiff();
        assertThat(events.size(), equalTo(2));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(0)),
                nodeEventForBaseline(),
                nodeEventWithDescription("Altered node state in cluster state from 'U' to 'M'"))));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(1)),
                nodeEventForBucketSpace("default"),
                nodeEventWithDescription("Altered node state in cluster state from 'U' to 'M'"))));
    }

    @Test
    public void derived_bucket_space_state_events_are_not_emitted_if_similar_to_baseline() {
        EventFixture f = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .derivedClusterStateBefore("default", "distributor:3 storage:3")
                .derivedClusterStateBefore("global", "distributor:3 storage:3")
                .clusterStateAfter("distributor:3 storage:3 .0.s:m")
                .derivedClusterStateAfter("default", "distributor:3 storage:3 .0.s:m")
                .derivedClusterStateAfter("global", "distributor:3 storage:3 .0.s:m");

        List<Event> events = f.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(0)),
                nodeEventForBaseline(),
                nodeEventWithDescription("Altered node state in cluster state from 'U' to 'M'"))));
    }

}
