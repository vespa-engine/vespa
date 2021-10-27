// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.vespa.clustercontroller.core.ActivateClusterStateVersionRequest;
import com.yahoo.vespa.clustercontroller.core.Communicator;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.Timer;

/**
 * Binds together the reply received for a particular cluster state activation RPC and
 * the cluster controller-internal callback handler which expects to receive it.
 */
public class RPCActivateClusterStateVersionWaiter implements RequestWaiter {

    private final Communicator.Waiter<ActivateClusterStateVersionRequest> waiter;
    private ActivateClusterStateVersionRequest request;

    public RPCActivateClusterStateVersionWaiter(Communicator.Waiter<ActivateClusterStateVersionRequest> waiter) {
        this.waiter = waiter;
    }

    public void setRequest(RPCActivateClusterStateVersionRequest request) {
        this.request = request;
    }

    public ActivateClusterStateVersionRequest.Reply getReply(Request req) {
        NodeInfo info = request.getNodeInfo();
        if (req.isError()) {
            return new ActivateClusterStateVersionRequest.Reply(req.errorCode(), req.errorMessage());
        } else if (!req.checkReturnTypes("i")) {
            return new ActivateClusterStateVersionRequest.Reply(ErrorCode.BAD_REPLY, "Got RPC response with invalid return types from " + info);
        }
        int actualVersion = req.returnValues().get(0).asInt32();
        return ActivateClusterStateVersionRequest.Reply.withActualVersion(actualVersion);
    }

    @Override
    public void handleRequestDone(Request request) {
        ActivateClusterStateVersionRequest.Reply reply = getReply(request);
        this.request.setReply(reply);
        waiter.done(this.request);
    }

}
