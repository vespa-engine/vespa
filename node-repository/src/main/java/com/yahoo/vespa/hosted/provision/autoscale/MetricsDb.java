// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * An in-memory time-series database of node metrics.
 *
 * @author bratseth
 */
public interface MetricsDb {

    /** Adds snapshots to this. */
    void add(Collection<Pair<String, MetricSnapshot>> nodeMetrics);

    /** Must be called intermittently (as long as add is called) to gc old data */
    void gc(Clock clock);

    /**
     * Returns a list of time series for each of the given host names
     * which have any values after startTime.
     */
    List<NodeTimeseries> getNodeTimeseries(Instant startTime, List<String> hostnames);

    void close();

    static MetricsDb createTestInstance(NodeRepository nodeRepository) {
        return new MemoryMetricsDb(nodeRepository);
    }

}
