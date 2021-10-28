// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.jrt.Request;
import com.yahoo.vespa.clustercontroller.core.ActivateClusterStateVersionRequest;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;

/**
 * FRT RPC state implementation of a single cluster state activation request.
 */
public class RPCActivateClusterStateVersionRequest extends ActivateClusterStateVersionRequest {

    Request request;

    public RPCActivateClusterStateVersionRequest(NodeInfo nodeInfo, Request request, int clusterStateVersion) {
        super(nodeInfo, clusterStateVersion);
        this.request = request;
    }

}
