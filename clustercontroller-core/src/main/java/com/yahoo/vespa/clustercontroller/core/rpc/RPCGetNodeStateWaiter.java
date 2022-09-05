// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.vespa.clustercontroller.core.Communicator;
import com.yahoo.vespa.clustercontroller.core.GetNodeStateRequest;

/**
 * Handles the reply to a get node state request to a node.
 */
public class RPCGetNodeStateWaiter implements RequestWaiter {

    private final RPCGetNodeStateRequest request;
    private final Communicator.Waiter<GetNodeStateRequest> waiter;

    public RPCGetNodeStateWaiter(RPCGetNodeStateRequest request, Communicator.Waiter<GetNodeStateRequest> waiter) {
        this.request = request;
        this.waiter = waiter;
    }

    private GetNodeStateRequest.Reply convertToReply(Request req) {
        if (req.isError()) {
            return new GetNodeStateRequest.Reply(req.errorCode(), req.errorMessage());
        }

        if (req.methodName().equals("getnodestate3")) {
            String stateStr = "";
            String hostInfo = "";

            if (req.returnValues().satisfies("s*")) {
                stateStr = req.returnValues().get(0).asString();
            }

            if (req.returnValues().satisfies("ss*")) {
                hostInfo = req.returnValues().get(1).asString();
            }

            return new GetNodeStateRequest.Reply(stateStr, hostInfo);
        } else {
            return new GetNodeStateRequest.Reply(ErrorCode.BAD_REPLY, "Unknown method name " + req.methodName());
        }
    }

    @Override
    public void handleRequestDone(Request req) {
        request.setReply(convertToReply(req));
        waiter.done(request);
    }

}
