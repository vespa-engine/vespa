// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.exhaustion;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.setOf;
import static com.yahoo.vespa.clustercontroller.core.matchers.EventForNode.eventForNode;
import static com.yahoo.vespa.clustercontroller.core.matchers.NodeEventForBucketSpace.nodeEventForBucketSpace;
import static com.yahoo.vespa.clustercontroller.core.matchers.NodeEventForBucketSpace.nodeEventForBaseline;
import static com.yahoo.vespa.clustercontroller.core.matchers.NodeEventWithDescription.nodeEventWithDescription;
import static com.yahoo.vespa.clustercontroller.core.matchers.ClusterEventWithDescription.clusterEventWithDescription;
import static com.yahoo.vespa.clustercontroller.core.matchers.EventTypeIs.eventTypeIs;
import static com.yahoo.vespa.clustercontroller.core.matchers.EventTimeIs.eventTimeIs;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.hasItem;

import static com.yahoo.vespa.clustercontroller.core.ClusterFixture.storageNode;
import static com.yahoo.vespa.clustercontroller.core.ClusterFixture.distributorNode;

import org.junit.jupiter.api.Test;

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
        ClusterStateBundle.FeedBlock feedBlockBefore = null;
        ClusterStateBundle.FeedBlock feedBlockAfter = null;
        long currentTimeMs = 0;
        long maxMaintenanceGracePeriodTimeMs = 10_000;

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
        EventFixture maxMaintenanceGracePeriodTimeMs(long timeMs) {
            this.maxMaintenanceGracePeriodTimeMs = timeMs;
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
        EventFixture feedBlockBefore(ClusterStateBundle.FeedBlock feedBlock) {
            this.feedBlockBefore = feedBlock;
            return this;
        }
        EventFixture feedBlockAfter(ClusterStateBundle.FeedBlock feedBlock) {
            this.feedBlockAfter = feedBlock;
            return this;
        }
        private static AnnotatedClusterState.Builder getBuilder(Map<String, AnnotatedClusterState.Builder> derivedStates, String bucketSpace) {
            return derivedStates.computeIfAbsent(bucketSpace, key -> new AnnotatedClusterState.Builder());
        }

        List<Event> computeEventDiff() {
            return EventDiffCalculator.computeEventDiff(
                    EventDiffCalculator.params()
                            .cluster(clusterFixture.cluster())
                            .fromState(ClusterStateBundle.of(baselineBefore.build(), toDerivedStates(derivedBefore), feedBlockBefore, false))
                            .toState(ClusterStateBundle.of(baselineAfter.build(), toDerivedStates(derivedAfter), feedBlockAfter, false))
                            .currentTimeMs(currentTimeMs)
                            .maxMaintenanceGracePeriodTimeMs(maxMaintenanceGracePeriodTimeMs));
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
    void single_storage_node_state_transition_emits_altered_node_state_event() {
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
    void single_distributor_node_state_transition_emits_altered_node_state_event() {
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
    void node_state_change_event_is_tagged_with_given_time() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("distributor:3 storage:3 .0.s:d")
                .currentTimeMs(123456);

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(eventTimeIs(123456)));
    }

    @Test
    void multiple_node_state_transitions_emit_multiple_node_state_events() {
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
    void no_emitted_node_state_event_when_node_state_not_changed() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("distributor:3 storage:3");

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(0));
    }

    @Test
    void node_down_edge_with_group_down_reason_has_separate_event_emitted() {
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
    void group_down_to_group_down_does_not_emit_new_event() {
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
    void group_down_to_clear_reason_emits_group_up_event() {
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
    void cluster_up_edge_emits_sufficient_node_availability_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("cluster:d distributor:3 storage:3")
                .clusterStateAfter("distributor:3 storage:3");

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(
                clusterEventWithDescription("Enough nodes available for system to become up")));
    }

    @Test
    void cluster_down_event_without_reason_annotation_emits_generic_down_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("cluster:d distributor:3 storage:3");

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(
                clusterEventWithDescription("Cluster is down")));
    }

    @Test
    void cluster_event_is_tagged_with_given_time() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .clusterStateAfter("cluster:d distributor:3 storage:3")
                .currentTimeMs(56789);

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(eventTimeIs(56789)));
    }

    @Test
    void no_event_emitted_for_cluster_down_to_down_edge() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("cluster:d distributor:3 storage:3")
                .clusterStateAfter("cluster:d distributor:3 storage:3");

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(0));
    }

    @Test
    void too_few_storage_nodes_cluster_down_reason_emits_corresponding_event() {
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
    void too_few_distributor_nodes_cluster_down_reason_emits_corresponding_event() {
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
    void too_low_storage_node_ratio_cluster_down_reason_emits_corresponding_event() {
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
    void too_low_distributor_node_ratio_cluster_down_reason_emits_corresponding_event() {
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
    void may_have_merges_pending_up_edge_event_emitted_if_derived_bucket_space_state_differs_from_baseline() {
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
    void may_have_merges_pending_down_edge_event_emitted_if_derived_bucket_space_state_differs_from_baseline() {
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
                nodeEventWithDescription("Node no longer has merges pending"))));
    }

    @Test
    void both_baseline_and_derived_bucket_space_state_events_are_emitted() {
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
    void derived_bucket_space_state_events_are_not_emitted_if_similar_to_baseline() {
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

    @Test
    void storage_node_passed_maintenance_grace_period_emits_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3 .0.s:m")
                .clusterStateAfter("distributor:3 storage:3 .0.s:d")
                .maxMaintenanceGracePeriodTimeMs(123_456)
                .storageNodeReasonAfter(0, NodeStateReason.NODE_NOT_BACK_UP_WITHIN_GRACE_PERIOD);

        final List<Event> events = fixture.computeEventDiff();
        // Down edge event + event explaining why the node went down
        assertThat(events.size(), equalTo(2));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(0)),
                nodeEventWithDescription("Exceeded implicit maintenance mode grace period of 123456 milliseconds. Marking node down."),
                nodeEventForBaseline())));
    }

    @Test
    void storage_node_maintenance_grace_period_event_only_emitted_on_maintenance_to_down_edge() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3 .0.s:u")
                .clusterStateAfter("distributor:3 storage:3 .0.s:d")
                .maxMaintenanceGracePeriodTimeMs(123_456)
                .storageNodeReasonAfter(0, NodeStateReason.NODE_NOT_BACK_UP_WITHIN_GRACE_PERIOD);
        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(0)),
                nodeEventForBaseline())));
    }

    @Test
    void feed_block_engage_edge_emits_cluster_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .feedBlockBefore(null)
                .clusterStateAfter("distributor:3 storage:3")
                .feedBlockAfter(ClusterStateBundle.FeedBlock.blockedWithDescription("we're closed"));

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(
                clusterEventWithDescription("Cluster feed blocked due to resource exhaustion: we're closed")));
    }

    @Test
    void feed_block_disengage_edge_emits_cluster_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .feedBlockBefore(ClusterStateBundle.FeedBlock.blockedWithDescription("we're closed"))
                .clusterStateAfter("distributor:3 storage:3")
                .feedBlockAfter(null);

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(clusterEventWithDescription("Cluster feed no longer blocked")));
    }

    @Test
    void feed_block_engaged_to_engaged_edge_does_not_emit_new_cluster_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .feedBlockBefore(ClusterStateBundle.FeedBlock.blockedWithDescription("we're closed"))
                .clusterStateAfter("distributor:3 storage:3")
                .feedBlockAfter(ClusterStateBundle.FeedBlock.blockedWithDescription("yep yep, still closed"));

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(0));
    }

    @Test
    void feed_block_engage_edge_with_node_exhaustion_info_emits_cluster_and_node_events() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .feedBlockBefore(null)
                .clusterStateAfter("distributor:3 storage:3")
                .feedBlockAfter(ClusterStateBundle.FeedBlock.blockedWith(
                        "we're closed", setOf(exhaustion(1, "oil"))));

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(2));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(1)),
                nodeEventWithDescription("Added resource exhaustion: oil on node 1 [unknown hostname] (0.800 > 0.700)"),
                nodeEventForBaseline())));
        assertThat(events, hasItem(
                clusterEventWithDescription("Cluster feed blocked due to resource exhaustion: we're closed")));
    }

    @Test
    void added_exhaustion_in_feed_block_resource_set_emits_node_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .feedBlockBefore(ClusterStateBundle.FeedBlock.blockedWith(
                        "we're closed", setOf(exhaustion(1, "oil"))))
                .clusterStateAfter("distributor:3 storage:3")
                .feedBlockAfter(ClusterStateBundle.FeedBlock.blockedWith(
                        "we're still closed", setOf(exhaustion(1, "oil"), exhaustion(1, "cpu_brake_fluid"))));

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(1)),
                nodeEventWithDescription("Added resource exhaustion: cpu_brake_fluid on node 1 [unknown hostname] (0.800 > 0.700)"),
                nodeEventForBaseline())));
    }

    @Test
    void removed_exhaustion_in_feed_block_resource_set_emits_node_event() {
        final EventFixture fixture = EventFixture.createForNodes(3)
                .clusterStateBefore("distributor:3 storage:3")
                .feedBlockBefore(ClusterStateBundle.FeedBlock.blockedWith(
                        "we're closed", setOf(exhaustion(1, "oil"), exhaustion(2, "cpu_brake_fluid"))))
                .clusterStateAfter("distributor:3 storage:3")
                .feedBlockAfter(ClusterStateBundle.FeedBlock.blockedWith(
                        "we're still closed", setOf(exhaustion(1, "oil"))));

        final List<Event> events = fixture.computeEventDiff();
        assertThat(events.size(), equalTo(1));
        assertThat(events, hasItem(allOf(
                eventForNode(storageNode(2)),
                nodeEventWithDescription("Removed resource exhaustion: cpu_brake_fluid on node 2 [unknown hostname] (<= 0.700)"),
                nodeEventForBaseline())));
    }

}
