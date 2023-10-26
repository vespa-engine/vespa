// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


class InvocationClient implements ReplyHandler, Runnable {

    Connection    conn;
    Request       req;
    double        timeout;
    RequestWaiter reqWaiter;
    Integer       replyKey;
    Task          timeoutTask;

    public InvocationClient(Connection conn, Request req,
                            double timeout, RequestWaiter waiter) {

        this.conn = conn;
        this.req = req;
        this.timeout = timeout;
        this.reqWaiter = waiter;
        req.clientHandler(this);

        this.replyKey = conn.allocateKey();
        this.timeoutTask = conn.transportThread().createTask(this);
    }

    public void invoke() {
        if (!conn.postPacket(new RequestPacket(0,
                                               replyKey.intValue(),
                                               req.methodName(),
                                               req.parameters()), this)) {
            req.setError(ErrorCode.CONNECTION, "Connection error");
            reqWaiter.handleRequestDone(req);
            return;
        }
        timeoutTask.schedule(timeout);
    }

    public Integer key() {
        return replyKey;
    }

    /**
     * Handle normal packet reply. The reply may contain either return
     * values or an error.
     **/
    public void handleReply(Packet packet) {
        timeoutTask.kill();
        if (packet == null) {
            req.setError(ErrorCode.BAD_REPLY, "Bad reply packet");
        } else {
            int pcode = packet.packetCode();
            if (pcode == Packet.PCODE_REPLY) {
                ReplyPacket rp = (ReplyPacket) packet;
                req.returnValues(rp.returnValues());
            } else if (pcode == Packet.PCODE_ERROR) {
                ErrorPacket ep = (ErrorPacket) packet;
                req.setError(ep.errorCode(), ep.errorMessage());
            }
        }
        reqWaiter.handleRequestDone(req);
    }

    /**
     * Handle user abort.
     **/
    public void handleAbort() {
        if (!conn.cancelReply(this)) {
            return;
        }
        timeoutTask.kill();
        req.setError(ErrorCode.ABORT, "Aborted by user");
        reqWaiter.handleRequestDone(req);
    }

    /**
     * Handle connection down.
     **/
    public void handleConnectionDown() {
        timeoutTask.kill();
        req.setError(ErrorCode.CONNECTION, "Connection error");
        reqWaiter.handleRequestDone(req);
    }

    /**
     * Handle timeout.
     **/
    public void run() {
        if (!conn.cancelReply(this)) {
            return;
        }
        req.setError(ErrorCode.TIMEOUT, "Request timed out after " + timeout + " seconds.");
        reqWaiter.handleRequestDone(req);
    }
}
