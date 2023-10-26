// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.provision.NodeList;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * @param hostnames the host names to return timeseries for, or empty to return for all hostnames
     */
    List<NodeTimeseries> getNodeTimeseries(Duration period, Set<String> hostnames);

    default List<NodeTimeseries> getNodeTimeseries(Duration period, NodeList nodes) {
        return getNodeTimeseries(period, nodes.hostnames());
    }

    /** Returns all cluster level metric snapshots for a given cluster */
    ClusterTimeseries getClusterTimeseries(ApplicationId applicationId, ClusterSpec.Id clusterId);

    /** Returns the number of times QuestDb has returned null records since last gc */
    default int getNullRecordsCount() { return 0; }

    /** Must be called intermittently (as long as add is called) to gc old data */
    void gc();

    void close();

    static MemoryMetricsDb createTestInstance(Clock clock) {
        return new MemoryMetricsDb(clock);
    }

}
