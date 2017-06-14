// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.hostinfo.StorageNodeStatsBridge;

/**
 * Class encapsulating what the Cluster Controller knows about a distributor node. Most of the information is
 * common between Storage- and Distributor- nodes, and stored in the base class NodeInfo.
 *
 * @author hakonhall
 */
public class DistributorNodeInfo extends NodeInfo {

    private StorageNodeStatsContainer storageNodeStatsContainer = null;

    public DistributorNodeInfo(ContentCluster cluster, int index, String rpcAddress, Distribution distribution) {
        super(cluster, new Node(NodeType.DISTRIBUTOR, index), false, rpcAddress, distribution);
    }

    @Override
    public void setHostInfo(HostInfo hostInfo) {
        // This affects getHostInfo(), and makes the host info available through NodeInfo.
        super.setHostInfo(hostInfo);
        storageNodeStatsContainer = StorageNodeStatsBridge.traverseHostInfo(hostInfo);
    }

    /**
     * @return Stats this distributor has about a storage node, or null if unknown.
     */
    public StorageNodeStats getStorageNodeStatsOrNull(int storageNodeIndex) {
        if (storageNodeStatsContainer == null) {
            return null;
        }

        return storageNodeStatsContainer.get(storageNodeIndex);
    }

}
