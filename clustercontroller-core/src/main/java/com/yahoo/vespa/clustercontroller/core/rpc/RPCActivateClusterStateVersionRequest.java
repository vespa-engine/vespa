// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.jrt.Request;
import com.yahoo.vespa.clustercontroller.core.ActivateClusterStateVersionRequest;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;

public class RPCActivateClusterStateVersionRequest extends ActivateClusterStateVersionRequest {

    Request request;

    public RPCActivateClusterStateVersionRequest(NodeInfo nodeInfo, Request request, int clusterStateVersion) {
        super(nodeInfo, clusterStateVersion);
        this.request = request;
    }

}
