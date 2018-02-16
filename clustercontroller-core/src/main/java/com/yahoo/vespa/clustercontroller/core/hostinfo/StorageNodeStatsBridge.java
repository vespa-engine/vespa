// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import com.yahoo.vespa.clustercontroller.core.*;
import com.yahoo.vespa.clustercontroller.core.ContentClusterStats;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class used to create a StorageNodeStatsContainer from HostInfo.
 *
 * @author hakonhall
 */
public class StorageNodeStatsBridge {

    private StorageNodeStatsBridge() { }

    public static StorageNodeStatsContainer traverseHostInfo(HostInfo hostInfo) {
        StorageNodeStatsContainer container = new StorageNodeStatsContainer();
        List<StorageNode> storageNodes = hostInfo.getDistributor().getStorageNodes();
        for (StorageNode storageNode : storageNodes) {
            Integer storageNodeIndex = storageNode.getIndex();
            if (storageNodeIndex == null) {
                continue;
            }
            StorageNode.OpsLatency opsLatency = storageNode.getOpsLatenciesOrNull();
            if (opsLatency == null) {
                continue;
            }
            StorageNode.Put putLatency = opsLatency.getPut();
            Long putLatencyMsSum = putLatency.getLatencyMsSum();
            Long putLatencyCount = putLatency.getCount();
            if (putLatencyMsSum == null || putLatencyCount == null) {
                continue;
            }
            LatencyStats putLatencyStats = new LatencyStats(putLatencyMsSum, putLatencyCount);
            StorageNodeStats nodeStats = new StorageNodeStats(putLatencyStats);
            container.put(storageNodeIndex, nodeStats);
        }
        return container;
    }

    public static ContentClusterStats generate(Distributor distributor) {
        Map<Integer, NodeMergeStats> mapToNodeStats = new HashMap<>();
        for (StorageNode storageNode : distributor.getStorageNodes()) {
            mapToNodeStats.put(storageNode.getIndex(), new NodeMergeStats(storageNode));
        }
        return new ContentClusterStats(mapToNodeStats);
    }

}
