// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterSpec;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A list of metric snapshots from a host
 *
 * @author bratseth
 */
public class NodeTimeseries {

    private final String hostname;
    private final ClusterSpec.Type type;
    private final List<MetricSnapshot> snapshots;

    // Note: This transfers ownership of the snapshot list to this
    NodeTimeseries(String hostname, ClusterSpec.Type type, List<MetricSnapshot> snapshots) {
        this.hostname = hostname;
        this.type = type;
        this.snapshots = snapshots;
    }

    public boolean isEmpty() { return snapshots.isEmpty(); }

    public int size() { return snapshots.size(); }

    public ClusterSpec.Type type() { return type; }

    public MetricSnapshot get(int index) { return snapshots.get(index); }

    public List<MetricSnapshot> asList() { return Collections.unmodifiableList(snapshots); }

    public String hostname() { return hostname; }

    public NodeTimeseries add(MetricSnapshot snapshot) {
        List<MetricSnapshot> list = new ArrayList<>(snapshots);
        list.add(snapshot);
        return new NodeTimeseries(hostname(), type(), list);
    }

    public NodeTimeseries justAfter(Instant oldestTime) {
        return new NodeTimeseries(hostname, type,
                                  snapshots.stream()
                                           .filter(snapshot -> snapshot.at().equals(oldestTime) || snapshot.at().isAfter(oldestTime))
                                           .collect(Collectors.toList()));
    }

}
