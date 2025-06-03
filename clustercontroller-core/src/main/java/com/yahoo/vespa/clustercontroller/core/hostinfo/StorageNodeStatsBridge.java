// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import com.yahoo.vespa.clustercontroller.core.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Class used to create a StorageNodeStatsContainer from HostInfo.
 *
 * @author hakonhall
 */
public class StorageNodeStatsBridge {

    private StorageNodeStatsBridge() { }

    public static ContentClusterStats generate(Distributor distributor) {
        Map<Integer, ContentNodeStats> mapToNodeStats = new HashMap<>();
        for (StorageNode storageNode : distributor.getStorageNodes()) {
            mapToNodeStats.put(storageNode.getIndex(), new ContentNodeStats(storageNode));
        }
        long docsTotal  = Optional.ofNullable(distributor.documentCountTotalOrNull()).orElse(0L);
        long bytesTotal = Optional.ofNullable(distributor.bytesTotalOrNull()).orElse(0L);
        return new ContentClusterStats(docsTotal, bytesTotal, mapToNodeStats);
    }

    public static ContentClusterErrorStats generateErrors(int distributorIndex, Distributor distributor) {
        Map<Integer, ContentNodeErrorStats> nodeErrorStats = new HashMap<>();
        for (StorageNode storageNode : distributor.getStorageNodes()) {
            if (storageNode.getResponseStats().hasNetworkErrors()) {
                // These will be aggregated up across distributors later.
                var nodeErrors = new ContentNodeErrorStats(storageNode.getIndex());
                nodeErrors.getStatsFromDistributors().put(distributorIndex,
                        ContentNodeErrorStats.DistributorErrorStats.fromHostInfoStats(storageNode.getResponseStats()));
                nodeErrorStats.put(storageNode.getIndex(), nodeErrors);
            }
        }
        return new ContentClusterErrorStats(nodeErrorStats);
    }

}
