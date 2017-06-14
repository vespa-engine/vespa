// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * Contains stats related to a single storage node.
 *
 * @author hakonhall
 */
public class StorageNodeStats {

    final private LatencyStats distributorPutLatency;

    /**
     * @param distributorPutLatency    The "put" latency from the point of view of the distributor.
     */
    public StorageNodeStats(LatencyStats distributorPutLatency) { this.distributorPutLatency = distributorPutLatency; }
    public LatencyStats getDistributorPutLatency() { return distributorPutLatency; }
    public void add(StorageNodeStats statsToAdd) {
        distributorPutLatency.add(statsToAdd.distributorPutLatency);
    }

}
