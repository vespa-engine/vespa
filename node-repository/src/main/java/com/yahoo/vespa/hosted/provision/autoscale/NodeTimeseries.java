// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.provision.autoscale.ClusterModel.warmupDuration;

/**
 * A list of metric snapshots from a node, sorted by increasing time (newest last).
 *
 * @author bratseth
 */
public class NodeTimeseries {

    private final String hostname;
    private final List<NodeMetricSnapshot> snapshots;

    NodeTimeseries(String hostname, List<NodeMetricSnapshot> snapshots) {
        this.hostname = hostname;
        List<NodeMetricSnapshot> sortedSnapshots = new ArrayList<>(snapshots);
        Collections.sort(sortedSnapshots);
        this.snapshots = Collections.unmodifiableList(sortedSnapshots);
    }

    public boolean isEmpty() { return snapshots.isEmpty(); }

    public int size() { return snapshots.size(); }

    public NodeMetricSnapshot get(int index) { return snapshots.get(index); }

    /** Returns the last (newest) snapshot in this, or empty if there are none. */
    public Optional<NodeMetricSnapshot> last() {
        if (snapshots.isEmpty()) return Optional.empty();
        return Optional.of(snapshots.get(snapshots.size() - 1));
    }

    public OptionalDouble peak(Load.Dimension dimension) {
        return snapshots.stream().mapToDouble(snapshot -> snapshot.load().get(dimension)).max();
    }

    public List<NodeMetricSnapshot> asList() { return snapshots; }

    public String hostname() { return hostname; }

    public NodeTimeseries add(NodeMetricSnapshot snapshot) {
        List<NodeMetricSnapshot> list = new ArrayList<>(snapshots);
        list.add(snapshot);
        return new NodeTimeseries(hostname(), list);
    }

    /** Returns the instant this changed to the given generation, or empty if no *change* to this generation is present */
    private Optional<Instant> generationChange(long targetGeneration) {
        if (snapshots.isEmpty()) return Optional.empty();
        if (snapshots.get(0).generation() == targetGeneration) return Optional.of(snapshots.get(0).at());
        for (NodeMetricSnapshot snapshot : snapshots) {
            if (snapshot.generation() == targetGeneration)
                return Optional.of(snapshot.at());
        }
        return Optional.empty();
    }

    public NodeTimeseries keep(Predicate<NodeMetricSnapshot> filter) {
        return new NodeTimeseries(hostname, snapshots.stream()
                                                     .filter(snapshot -> filter.test(snapshot))
                                                     .collect(Collectors.toList()));
    }

    public NodeTimeseries keepAfter(Instant oldestTime) {
        return new NodeTimeseries(hostname,
                                  snapshots.stream()
                                           .filter(snapshot -> snapshot.at().equals(oldestTime) || snapshot.at().isAfter(oldestTime))
                                           .collect(Collectors.toList()));
    }

    public NodeTimeseries keepCurrentGenerationAfterWarmup(long currentGeneration) {
        Optional<Instant> generationChange = generationChange(currentGeneration);
        return keep(snapshot -> isOnCurrentGenerationAfterWarmup(snapshot, currentGeneration, generationChange));
    }

    private boolean isOnCurrentGenerationAfterWarmup(NodeMetricSnapshot snapshot,
                                                     long currentGeneration,
                                                     Optional<Instant> generationChange) {
        if (snapshot.generation() < 0) return true; // Content nodes do not yet send generation
        if (snapshot.generation() < currentGeneration) return false;
        if (generationChange.isEmpty()) return true;
        return ! snapshot.at().isBefore(generationChange.get().plus(warmupDuration));
    }

}
