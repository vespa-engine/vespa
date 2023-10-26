// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc.messagebus;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.TraceNode;
import com.yahoo.messagebus.jdisc.MbusResponse;
import com.yahoo.messagebus.jdisc.StatusCodes;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Einar M R Rosenvinge
 */
class ResponseMerger implements ResponseHandler {

    private final Message requestMsg;
    private final TraceNode requestTrace = new TraceNode().setStrict(false);
    private final ResponseHandler responseHandler;
    private final List<Reply> replies;
    private int numPending;

    public ResponseMerger(Message requestMsg, int numPending, ResponseHandler responseHandler) {
        this.requestMsg = requestMsg;
        this.responseHandler = responseHandler;
        this.replies = new ArrayList<>(numPending);
        this.numPending = numPending;
    }

    @Override
    public ContentChannel handleResponse(Response response) {
        synchronized (this) {
            if (response instanceof MbusResponse) {
                Reply reply = ((MbusResponse)response).getReply();
                requestTrace.addChild(reply.getTrace().getRoot());
                replies.add(reply);
            }
            if (--numPending != 0) {
                return null;
            }
        }
        requestMsg.getTrace().getRoot().addChild(requestTrace);
        Reply reply = DocumentProtocol.merge(replies);
        Response mbusResponse = new MbusResponse(StatusCodes.fromMbusReply(reply), reply);
        ResponseDispatch.newInstance(mbusResponse).dispatch(responseHandler);
        return null;
    }

}
