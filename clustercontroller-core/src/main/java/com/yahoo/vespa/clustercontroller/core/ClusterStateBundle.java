// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A cluster state bundle is a wrapper around the baseline ("source of truth") cluster
 * state and any bucket space specific states that may be derived from it.
 *
 * The baseline state represents the generated state of the _nodes_ in the cluster,
 * while the per-space states represent possible transformations that make sense in
 * the context of that particular bucket space. The most prominent example is
 * transforming nodes in the default bucket space into maintenance mode if they have
 * merges pending in the global space.
 *
 * The baseline state is identical to the legacy, global cluster state that the
 * cluster controller has historically produced as its only output.
 */
public class ClusterStateBundle {

    private final AnnotatedClusterState baselineState;
    private final Map<String, AnnotatedClusterState> derivedBucketSpaceStates;

    public static class Builder {
        private final AnnotatedClusterState baselineState;
        private ClusterStateDeriver stateDeriver;
        private Set<String> bucketSpaces;

        public Builder(AnnotatedClusterState baselineState) {
            this.baselineState = baselineState;
        }

        public Builder stateDeriver(ClusterStateDeriver stateDeriver) {
            this.stateDeriver = stateDeriver;
            return this;
        }

        public Builder bucketSpaces(Set<String> bucketSpaces) {
            this.bucketSpaces = bucketSpaces;
            return this;
        }

        public Builder bucketSpaces(String... bucketSpaces) {
            this.bucketSpaces = new TreeSet<>(Arrays.asList(bucketSpaces));
            return this;
        }

        public ClusterStateBundle deriveAndBuild() {
            if (stateDeriver == null || bucketSpaces == null || bucketSpaces.isEmpty()) {
                return ClusterStateBundle.ofBaselineOnly(baselineState);
            }
            Map<String, AnnotatedClusterState> derived = bucketSpaces.stream()
                    .collect(Collectors.toMap(
                            Function.identity(),
                            s -> stateDeriver.derivedFrom(baselineState, s)));
            return new ClusterStateBundle(baselineState, derived);
        }
    }

    private ClusterStateBundle(AnnotatedClusterState baselineState, Map<String, AnnotatedClusterState> derivedBucketSpaceStates) {
        this.baselineState = baselineState;
        this.derivedBucketSpaceStates = Collections.unmodifiableMap(derivedBucketSpaceStates);
    }

    public static Builder builder(AnnotatedClusterState baselineState) {
        return new Builder(baselineState);
    }

    public static ClusterStateBundle of(AnnotatedClusterState baselineState, Map<String, AnnotatedClusterState> derivedBucketSpaceStates) {
        return new ClusterStateBundle(baselineState, derivedBucketSpaceStates);
    }

    public static ClusterStateBundle ofBaselineOnly(AnnotatedClusterState baselineState) {
        return new ClusterStateBundle(baselineState, Collections.emptyMap());
    }

    public AnnotatedClusterState getBaselineAnnotatedState() {
        return baselineState;
    }

    public ClusterState getBaselineClusterState() {
        return baselineState.getClusterState();
    }

    public Map<String, AnnotatedClusterState> getDerivedBucketSpaceStates() {
        return derivedBucketSpaceStates;
    }

    public ClusterStateBundle cloneWithMapper(Function<ClusterState, ClusterState> mapper) {
        AnnotatedClusterState clonedBaseline = baselineState.cloneWithClusterState(
                mapper.apply(baselineState.getClusterState().clone()));
        Map<String, AnnotatedClusterState> clonedDerived = derivedBucketSpaceStates.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().cloneWithClusterState(
                        mapper.apply(e.getValue().getClusterState().clone()))));
        return new ClusterStateBundle(clonedBaseline, clonedDerived);
    }

    public ClusterStateBundle clonedWithVersionSet(int version) {
        return cloneWithMapper(state -> {
            state.setVersion(version);
            return state;
        });
    }

    public boolean similarTo(ClusterStateBundle other) {
        if (!baselineState.getClusterState().similarToIgnoringInitProgress(other.baselineState.getClusterState())) {
            return false;
        }
        // FIXME we currently treat mismatching bucket space sets as unchanged to avoid breaking some tests
        return derivedBucketSpaceStates.entrySet().stream()
                .allMatch(entry -> other.derivedBucketSpaceStates.getOrDefault(entry.getKey(), entry.getValue())
                        .getClusterState().similarToIgnoringInitProgress(entry.getValue().getClusterState()));
    }

    public int getVersion() {
        return baselineState.getClusterState().getVersion();
    }

    @Override
    public String toString() {
        if (derivedBucketSpaceStates.isEmpty()) {
            return String.format("ClusterStateBundle('%s')", baselineState);
        }
        Map<String, AnnotatedClusterState> orderedStates = new TreeMap<>(derivedBucketSpaceStates);
        return String.format("ClusterStateBundle('%s', %s)", baselineState, orderedStates.entrySet().stream()
                .map(e -> String.format("%s '%s'", e.getKey(), e.getValue()))
                .collect(Collectors.joining(", ")));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterStateBundle that = (ClusterStateBundle) o;
        return Objects.equals(baselineState, that.baselineState) &&
                Objects.equals(derivedBucketSpaceStates, that.derivedBucketSpaceStates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baselineState, derivedBucketSpaceStates);
    }

}
