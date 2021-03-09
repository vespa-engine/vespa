// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

    public List<NodeMetricSnapshot> asList() { return snapshots; }

    public String hostname() { return hostname; }

    public NodeTimeseries add(NodeMetricSnapshot snapshot) {
        List<NodeMetricSnapshot> list = new ArrayList<>(snapshots);
        list.add(snapshot);
        return new NodeTimeseries(hostname(), list);
    }

    public NodeTimeseries filter(Predicate<NodeMetricSnapshot> filter) {
        return new NodeTimeseries(hostname, snapshots.stream().filter(filter).collect(Collectors.toList()));
    }

    public NodeTimeseries justAfter(Instant oldestTime) {
        return new NodeTimeseries(hostname,
                                  snapshots.stream()
                                           .filter(snapshot -> snapshot.at().equals(oldestTime) || snapshot.at().isAfter(oldestTime))
                                           .collect(Collectors.toList()));
    }

}
