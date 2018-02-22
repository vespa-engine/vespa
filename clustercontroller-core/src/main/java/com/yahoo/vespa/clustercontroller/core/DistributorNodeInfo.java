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

    public DistributorNodeInfo(ContentCluster cluster, int index, String rpcAddress, Distribution distribution) {
        super(cluster, new Node(NodeType.DISTRIBUTOR, index), false, rpcAddress, distribution);
    }

}
