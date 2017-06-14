// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Contains stats for a set of storage nodes. This is used to store the stats returned
 * by Distributors from their getnodestate RPCs. The stats for a single storage node
 * is represented by the StorageNodeStats class.
 *
 * @author hakonhall
 */
public class StorageNodeStatsContainer {

    final private Map<Integer, StorageNodeStats> storageNodesByIndex = new HashMap<>();

    public void put(int nodeIndex, StorageNodeStats nodeStats) {
        storageNodesByIndex.put(nodeIndex, nodeStats);
    }

    public StorageNodeStats get(int nodeIndex) {
        return storageNodesByIndex.get(nodeIndex);
    }

    public int size() { return storageNodesByIndex.size(); }
}
