// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Keeps track of the active cluster state and handles the transition edges between
 * one state to the next. In particular, it ensures that states have strictly increasing
 * version numbers.
 *
 * Wraps ClusterStateView to ensure its knowledge of available nodes stays up to date.
 */
public class StateVersionTracker {

    // We always increment the version _before_ publishing, so the effective first cluster
    // state version when starting from 1 will be 2. This matches legacy behavior and a bunch
    // of existing tests expect it.
    private int currentVersion = 1;
    private int lastZooKeeperVersion = 0;

    // The lowest published distribution bit count for the lifetime of this controller.
    // TODO this mirrors legacy behavior, but should be moved into stable ZK state.
    private int lowestObservedDistributionBits = 16;

    private ClusterState currentUnversionedState = ClusterState.emptyState();
    private AnnotatedClusterState latestCandidateState = AnnotatedClusterState.emptyState();
    private AnnotatedClusterState currentClusterState = latestCandidateState;

    private ClusterStateView clusterStateView;

    private final LinkedList<ClusterStateHistoryEntry> clusterStateHistory = new LinkedList<>();
    private int maxHistoryEntryCount = 50;

    StateVersionTracker() {
        clusterStateView = ClusterStateView.create(currentUnversionedState);
    }

    void setVersionRetrievedFromZooKeeper(final int version) {
        this.currentVersion = Math.max(1, version);
        this.lastZooKeeperVersion = this.currentVersion;
    }

    /**
     * Sets limit on how many cluster states can be kept in the in-memory queue. Once
     * the list exceeds this limit, the oldest state is repeatedly removed until the limit
     * is no longer exceeded.
     *
     * Takes effect upon the next invocation of promoteCandidateToVersionedState().
     */
    void setMaxHistoryEntryCount(final int maxHistoryEntryCount) {
        this.maxHistoryEntryCount = maxHistoryEntryCount;
    }

    int getCurrentVersion() {
        return this.currentVersion;
    }

    boolean hasReceivedNewVersionFromZooKeeper() {
        return currentVersion <= lastZooKeeperVersion;
    }

    int getLowestObservedDistributionBits() {
        return lowestObservedDistributionBits;
    }

    AnnotatedClusterState getAnnotatedVersionedClusterState() {
        return currentClusterState;
    }

    public ClusterState getVersionedClusterState() {
        return currentClusterState.getClusterState();
    }

    public void updateLatestCandidateState(final AnnotatedClusterState candidate) {
        assert(latestCandidateState.getClusterState().getVersion() == 0);
        latestCandidateState = candidate;
    }

    /**
     * Returns the last state provided to updateLatestCandidateState, which _may or may not_ be
     * a published state. Primary use case for this function is a caller which is interested in
     * changes that may not be reflected in the published state. The best example of this would
     * be node state changes when a cluster is marked as Down.
     */
    public AnnotatedClusterState getLatestCandidateState() {
        return latestCandidateState;
    }

    public List<ClusterStateHistoryEntry> getClusterStateHistory() {
        return Collections.unmodifiableList(clusterStateHistory);
    }

    boolean candidateChangedEnoughFromCurrentToWarrantPublish() {
        return !currentUnversionedState.similarToIgnoringInitProgress(latestCandidateState.getClusterState());
    }

    void promoteCandidateToVersionedState(final long currentTimeMs) {
        final int newVersion = currentVersion + 1;
        updateStatesForNewVersion(latestCandidateState, newVersion);
        currentVersion = newVersion;

        recordCurrentStateInHistoryAtTime(currentTimeMs);
    }

    private void updateStatesForNewVersion(final AnnotatedClusterState newState, final int newVersion) {
        currentClusterState = new AnnotatedClusterState(
                newState.getClusterState().clone(), // Because we mutate version below
                newState.getClusterStateReason(),
                newState.getNodeStateReasons());
        currentClusterState.getClusterState().setVersion(newVersion);
        currentUnversionedState = newState.getClusterState().clone();
        lowestObservedDistributionBits = Math.min(
                lowestObservedDistributionBits,
                newState.getClusterState().getDistributionBitCount());
        // TODO should this take place in updateLatestCandidateState instead? I.e. does it require a consolidated state?
        clusterStateView = ClusterStateView.create(currentClusterState.getClusterState());
    }

    private void recordCurrentStateInHistoryAtTime(final long currentTimeMs) {
        clusterStateHistory.addFirst(new ClusterStateHistoryEntry(
                currentClusterState.getClusterState(), currentTimeMs));
        while (clusterStateHistory.size() > maxHistoryEntryCount) {
            clusterStateHistory.removeLast();
        }
    }

    void handleUpdatedHostInfo(final Map<Integer, String> hostnames, final NodeInfo node, final HostInfo hostInfo) {
        // TODO the wiring here isn't unit tested. Need mockable integration points.
        clusterStateView.handleUpdatedHostInfo(hostnames, node, hostInfo);
    }

}
