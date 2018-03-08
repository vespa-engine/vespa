// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.document.FixedBucketSpaces;
import com.yahoo.vdslib.state.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cluster state deriver which checks if nodes have merges pending for globally
 * distributed documents, and if they do, sets the node in maintenance mode in the
 * default bucket space. This allows merges to complete for global documents before
 * the default space documents that have references to them are made searchable.
 *
 * Note: bucket spaces are currently hard-coded.
 */
public class MaintenanceWhenPendingGlobalMerges implements ClusterStateDeriver {

    // TODO make these configurable
    private static final String bucketSpaceToCheck = FixedBucketSpaces.globalSpace();
    private static final String bucketSpaceToDerive = FixedBucketSpaces.defaultSpace();

    private final MergePendingChecker mergePendingChecker;
    private final MaintenanceTransitionConstraint maintenanceTransitionConstraint;

    public MaintenanceWhenPendingGlobalMerges(MergePendingChecker mergePendingChecker,
                                              MaintenanceTransitionConstraint maintenanceTransitionConstraint) {
        this.mergePendingChecker = mergePendingChecker;
        this.maintenanceTransitionConstraint = maintenanceTransitionConstraint;
    }

    @Override
    public AnnotatedClusterState derivedFrom(AnnotatedClusterState baselineState, String bucketSpace) {
        if (!bucketSpace.equals(bucketSpaceToDerive)) {
            return baselineState.clone();
        }
        Set<Integer> incompleteNodeIndices = availableContentNodes(baselineState.getClusterState()).stream()
                .filter(nodeIndex -> mayHaveMergesPending(bucketSpaceToCheck, nodeIndex))
                .filter(maintenanceTransitionConstraint::maintenanceTransitionAllowed)
                .collect(Collectors.toSet());

        if (incompleteNodeIndices.isEmpty()) {
            return baselineState.clone();
        }
        return setNodesInMaintenance(baselineState, incompleteNodeIndices);
    }

    private static Set<Integer> availableContentNodes(ClusterState baselineState) {
        final Set<Integer> availableNodes = new HashSet<>();
        final int nodeCount = baselineState.getNodeCount(NodeType.STORAGE);
        for (int nodeIndex = 0; nodeIndex < nodeCount; ++nodeIndex) {
            if (contentNodeIsAvailable(baselineState, nodeIndex)) {
                availableNodes.add(nodeIndex);
            }
        }
        return availableNodes;
    }

    private AnnotatedClusterState setNodesInMaintenance(AnnotatedClusterState baselineState,
                                                        Set<Integer> incompleteNodeIndices) {
        ClusterState derivedState = baselineState.getClusterState().clone();
        Map<Node, NodeStateReason> nodeStateReasons = new HashMap<>(baselineState.getNodeStateReasons());
        incompleteNodeIndices.forEach(nodeIndex -> {
            Node node = Node.ofStorage(nodeIndex);
            derivedState.setNodeState(node, new NodeState(NodeType.STORAGE, State.MAINTENANCE));
            nodeStateReasons.put(node, NodeStateReason.MAY_HAVE_MERGES_PENDING);
        });
        return new AnnotatedClusterState(derivedState,
                baselineState.getClusterStateReason(),
                nodeStateReasons);
    }

    private static boolean contentNodeIsAvailable(ClusterState state, int nodeIndex) {
        return state.getNodeState(Node.ofStorage(nodeIndex)).getState().oneOf("uir");
    }

    private boolean mayHaveMergesPending(String bucketSpace, int nodeIndex) {
        return mergePendingChecker.mayHaveMergesPending(bucketSpace, nodeIndex);
    }
}
