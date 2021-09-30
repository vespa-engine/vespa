// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Represents the absolute cluster states (baseline + derived) for a given version
 * as well as the relative diffs (baseline + derived) since the previous version.
 */
public class ClusterStateHistoryEntry {

    public final static String BASELINE = "-";

    private final long time;
    private final Map<String, String> states = new TreeMap<>();
    private final Map<String, String> diffs = new TreeMap<>();

    ClusterStateHistoryEntry(ClusterStateBundle state, long time) {
        this.time = time;
        populateStateStrings(state);
        // No diffs for the first entry in the history.
    }

    ClusterStateHistoryEntry(ClusterStateBundle state, ClusterStateBundle prevState, long time) {
        this.time = time;
        populateStateStrings(state);
        populateDiffStrings(state, prevState);
    }

    private void populateStateStrings(ClusterStateBundle state) {
        states.put(BASELINE, state.getBaselineClusterState().toString());
        var derivedStates = state.getDerivedBucketSpaceStates().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey(),
                        entry -> entry.getValue().getClusterState().toString()));
        states.putAll(derivedStates);
    }

    private void populateDiffStrings(ClusterStateBundle state, ClusterStateBundle prevState) {
        // Yes, it's correct to get the diff by doing old.getHtmlDifference(new) rather than the other way around.
        diffs.put(BASELINE, prevState.getBaselineClusterState().getHtmlDifference(state.getBaselineClusterState()));
        var spaceDiffs = state.getDerivedBucketSpaceStates().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey(),
                        entry -> derivedStateOf(prevState, entry.getKey()).getHtmlDifference(entry.getValue().getClusterState())));
        diffs.putAll(spaceDiffs);
    }

    public static ClusterStateHistoryEntry makeFirstEntry(ClusterStateBundle state, long time) {
        return new ClusterStateHistoryEntry(state, time);
    }

    public static ClusterStateHistoryEntry makeSuccessor(ClusterStateBundle state, ClusterStateBundle prevState, long time) {
        return new ClusterStateHistoryEntry(state, prevState, time);
    }

    private static ClusterState derivedStateOf(ClusterStateBundle state, String space) {
        return state.getDerivedBucketSpaceStates().getOrDefault(space, AnnotatedClusterState.emptyState()).getClusterState();
    }

    public Map<String, String> getRawStates() {
        return states;
    }

    public String getStateString(String space) {
        return states.getOrDefault(space, "");
    }

    public String getDiffString(String space) {
        return diffs.getOrDefault(space, "");
    }

    public long time() {
        return time;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterStateHistoryEntry that = (ClusterStateHistoryEntry) o;
        return time == that.time &&
                Objects.equals(states, that.states) &&
                Objects.equals(diffs, that.diffs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, states, diffs);
    }

    // String representation only used for test expectation failures and debugging output.
    // Actual status page history entry rendering emits formatted date/time.
    public String toString() {
        return String.format("state '%s' at time %d", getStateString(BASELINE), time);
    }

}
