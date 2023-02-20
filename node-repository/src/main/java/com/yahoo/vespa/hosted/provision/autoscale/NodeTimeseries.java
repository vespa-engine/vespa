// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Predicate;

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
                                                     .toList());
    }

    public NodeTimeseries keepAfter(Instant oldestTime) {
        return new NodeTimeseries(hostname,
                                  snapshots.stream()
                                           .filter(snapshot -> snapshot.at().equals(oldestTime) || snapshot.at().isAfter(oldestTime))
                                           .toList());
    }

    public NodeTimeseries keepGenerationAfterWarmup(long generation, Optional<Node> node) {
        Optional<Instant> generationChange = generationChange(generation);
        return keep(snapshot -> isOnGenerationAfterWarmup(snapshot, node, generation, generationChange));
    }
    private boolean isOnGenerationAfterWarmup(NodeMetricSnapshot snapshot,
                                              Optional<Node> node,
                                              long generation,
                                              Optional<Instant> generationChange) {
        if ( ! node.isPresent()) return false; // Node has been removed
        if ( ! onAtLeastGeneration(generation, snapshot)) return false;
        if (recentlyChangedGeneration(snapshot, generationChange)) return false;
        if (recentlyCameUp(snapshot, node.get())) return false;
        return true;
    }

    private boolean onAtLeastGeneration(long generation, NodeMetricSnapshot snapshot) {
        return snapshot.generation() >= generation;
    }

    private boolean recentlyChangedGeneration(NodeMetricSnapshot snapshot, Optional<Instant> generationChange) {
        if (generationChange.isEmpty()) return false;
        return snapshot.at().isBefore(generationChange.get().plus(warmupDuration));
    }

    private boolean recentlyCameUp(NodeMetricSnapshot snapshot, Node node) {
        Optional<History.Event> up = node.history().event(History.Event.Type.up);
        return up.isPresent() && snapshot.at().isBefore(up.get().at().plus(warmupDuration));
    }

}
