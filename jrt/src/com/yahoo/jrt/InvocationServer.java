// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


class InvocationServer {

    private Connection conn;
    private Request    request;
    private Method     method;
    private int        replyKey;
    private boolean    noReply;
    private TieBreaker done;

    public InvocationServer(Connection conn, Request request, Method method,
                            int replyKey, boolean noReply) {

        this.conn = conn;
        this.request = request;
        this.method = method;
        this.replyKey = replyKey;
        this.noReply = noReply;
        request.serverHandler(this);

        done = conn.startRequest();
    }

    public Target getTarget() {
        return conn;
    }

    public void invoke() {
        if (method != null) {
            if (method.checkParameters(request)) {
                if (method.requestAccessFilter().allow(request)) {
                    method.invoke(request);
                } else {
                    request.setError(ErrorCode.PERMISSION_DENIED, "Permission denied");
                }
            } else {
                request.setError(ErrorCode.WRONG_PARAMS, "Parameters in " + request + " does not match " + method);
            }
        } else {
            request.setError(ErrorCode.NO_SUCH_METHOD, "No such method");
        }
        if (!request.isDetached()) {
            returnRequest();
        }
    }

    public void returnRequest() {
        if (!conn.completeRequest(done)) {
            throw new IllegalStateException("Request already returned");
        }
        if (noReply) {
            return;
        }
        if (!request.isError() && !method.checkReturnValues(request)) {
            request.setError(ErrorCode.WRONG_RETURN, "Return values in " + request + " does not match " + method);
        }
        if (request.isError()) {
            conn.postPacket(new ErrorPacket(0, replyKey,
                                            request.errorCode(),
                                            request.errorMessage()));
        } else {
            conn.postPacket(new ReplyPacket(0, replyKey,
                                            request.returnValues()));
        }
    }
}
