// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.vespa.clustercontroller.core.Communicator;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.SetClusterStateRequest;
import com.yahoo.vespa.clustercontroller.core.Timer;

/**
 * Waiter class for set cluster state RPC commands.
 */
public class RPCSetClusterStateWaiter implements RequestWaiter {

    SetClusterStateRequest request;
    Timer timer;
    Communicator.Waiter<SetClusterStateRequest> waiter;

    public RPCSetClusterStateWaiter(Communicator.Waiter<SetClusterStateRequest> waiter, Timer timer) {
        this.timer = timer;
        this.waiter = waiter;
    }

    public void setRequest(RPCSetClusterStateRequest request) {
        this.request = request;
    }

    public SetClusterStateRequest.Reply getReply(Request req) {
        NodeInfo info = request.getNodeInfo();

        if (req.methodName().equals(RPCCommunicator.SET_DISTRIBUTION_STATES_RPC_METHOD_NAME)
                || req.methodName().equals(RPCCommunicator.LEGACY_SET_SYSTEM_STATE2_RPC_METHOD_NAME)) {
            if (req.isError() && req.errorCode() == ErrorCode.NO_SUCH_METHOD) {
                if (info.notifyNoSuchMethodError(req.methodName(), timer)) {
                    return new SetClusterStateRequest.Reply(Communicator.TRANSIENT_ERROR, "Trying lower version");
                }
            }
            if (req.isError()) {
                return new SetClusterStateRequest.Reply(req.errorCode(), req.errorMessage());
            } else if (!req.checkReturnTypes("")) {
                return new SetClusterStateRequest.Reply(ErrorCode.BAD_REPLY, "Got RPC response with invalid return types from " + info);
            }
        } else {
            return new SetClusterStateRequest.Reply(ErrorCode.BAD_REPLY, "Unknown method " + req.methodName());
        }

        return new SetClusterStateRequest.Reply();
    }

    @Override
    public void handleRequestDone(Request request) {
        SetClusterStateRequest.Reply reply = getReply(request);
        this.request.setReply(reply);
        waiter.done(this.request);
    }

}
