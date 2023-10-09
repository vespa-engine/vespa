// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.jrt.Request;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.SetClusterStateRequest;

public class RPCSetClusterStateRequest extends SetClusterStateRequest {

    Request request;

    public RPCSetClusterStateRequest(NodeInfo nodeInfo, Request request, int clusterStateVersion) {
        super(nodeInfo, clusterStateVersion);
        this.request = request;
    }

}
