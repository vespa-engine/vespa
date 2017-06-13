// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.Map;

/**
 * Contains stats for a set of storage nodes. This is used to store the stats returned
 * by Distributors from their getnodestate RPCs. The stats for a single storage node
 * is represented by the StorageNodeStats class.
 *
 * @author hakonhall
 */
public class StatsForStorageNodes {

    final private Map<Integer, StorageNodeStats> storageNodesByIndex;

    StatsForStorageNodes(Map<Integer, StorageNodeStats> storageNodesByIndex) {
        this.storageNodesByIndex = storageNodesByIndex;
    }

    StorageNodeStats getStatsForStorageNode(int nodeIndex) {
        return storageNodesByIndex.get(nodeIndex);
    }

}
