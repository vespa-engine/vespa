// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;

public class StateVersionTracker {
    // We always increment the version _before_ publishing, so the effective first cluster
    // state version when starting from 1 will be 2. This matches legacy behavior and a bunch
    // of existing tests expect it.
    private int currentVersion = 1;
    private int lastZooKeeperVersion = 0;
    // The lowest published distribution bit count for the lifetime of this controller.
    // TODO this mirrors legacy behavior, but should be moved into stable ZK state.
    private int lowestObservedDistributionBits = 16;
    // TODO ClusterStateView integration; how/where?
    private ClusterState currentUnversionedState = ClusterStateUtil.emptyState();
    private AnnotatedClusterState currentClusterState = AnnotatedClusterState.emptyState();

    public void setVersionRetrievedFromZooKeeper(int version) {
        this.currentVersion = Math.max(1, version);
        this.lastZooKeeperVersion = this.currentVersion;
    }

    public int getCurrentVersion() {
        return this.currentVersion;
    }

    public boolean hasReceivedNewVersionFromZooKeeper() {
        return currentVersion <= lastZooKeeperVersion;
    }

    public int getLowestObservedDistributionBits() {
        return lowestObservedDistributionBits;
    }

    public AnnotatedClusterState getAnnotatedClusterState() {
        return currentClusterState;
    }

    public ClusterState getVersionedClusterState() {
        return currentClusterState.getClusterState();
    }

    public boolean changedEnoughFromCurrentToWarrantBroadcast(final AnnotatedClusterState candidate) {
        return !currentUnversionedState.similarTo(candidate.getClusterState());
    }

    public void applyAndVersionNewState(final AnnotatedClusterState newState) {
        assert newState.getClusterState().getVersion() == 0; // Should not be explicitly set

        final int newVersion = currentVersion + 1;
        currentClusterState = new AnnotatedClusterState(
                newState.getClusterState().clone(), // Because we mutate version below
                newState.getClusterStateReason(),
                newState.getNodeStateReasons());
        currentClusterState.getClusterState().setVersion(newVersion);
        currentUnversionedState = newState.getClusterState();
        lowestObservedDistributionBits = Math.min(
                lowestObservedDistributionBits,
                newState.getClusterState().getDistributionBitCount());

        currentVersion = newVersion;
    }
}
