// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeType;

/**
 * Class encapsulating what the Cluster Controller knows about a storage node. Most of the information is
 * common between Storage- and Distributor- nodes, and stored in the base class NodeInfo.
 *
 * @author hakonhall
 */
public class StorageNodeInfo extends NodeInfo {

    public StorageNodeInfo(ContentCluster cluster, int index, boolean configuredRetired, String rpcAddress, Distribution distribution) {
        super(cluster, new Node(NodeType.STORAGE, index), configuredRetired, rpcAddress, distribution);
    }

}
