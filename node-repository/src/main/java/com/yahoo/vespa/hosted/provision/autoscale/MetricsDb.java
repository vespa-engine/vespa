// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An in-memory time-series database of node metrics.
 *
 * @author bratseth
 */
public interface MetricsDb {

    Clock clock();

    /** Adds node snapshots to this. */
    void addNodeMetrics(Collection<Pair<String, NodeMetricSnapshot>> nodeMetrics);

    void addClusterMetrics(ApplicationId application, Map<ClusterSpec.Id,  ClusterMetricSnapshot> clusterMetrics);

    /**
     * Returns a list with one entry for each hostname containing
     * the snapshots recorded after the given time (or an empty snapshot if none).
     *
     * @param period the duration into the past to return data for
     */
    List<NodeTimeseries> getNodeTimeseries(Duration period, Set<String> hostnames);

    default List<NodeTimeseries> getNodeTimeseries(Duration period, NodeList nodes) {
        return getNodeTimeseries(period, nodes.stream().map(Node::hostname).collect(Collectors.toSet()));
    }

    /** Returns all cluster level metric snapshots for a given cluster */
    ClusterTimeseries getClusterTimeseries(ApplicationId applicationId, ClusterSpec.Id clusterId);

    /** Must be called intermittently (as long as add is called) to gc old data */
    void gc();

    void close();

    static MemoryMetricsDb createTestInstance(NodeRepository nodeRepository) {
        return new MemoryMetricsDb(nodeRepository);
    }

}
