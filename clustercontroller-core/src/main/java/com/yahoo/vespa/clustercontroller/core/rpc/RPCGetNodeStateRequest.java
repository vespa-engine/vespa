// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.jrt.Request;
import com.yahoo.vespa.clustercontroller.core.GetNodeStateRequest;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;

public class RPCGetNodeStateRequest extends GetNodeStateRequest {

    Request request;

    public RPCGetNodeStateRequest(NodeInfo nodeInfo, Request request) {
        super(nodeInfo);
        this.request = request;
    }

    @Override
    public void abort() {
        request.abort();
    }

}
