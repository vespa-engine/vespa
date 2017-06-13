// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

public abstract class SetClusterStateRequest {

    private final NodeInfo nodeInfo;
    private final int systemStateVersion;
    private Reply reply;

    public SetClusterStateRequest(NodeInfo nodeInfo, int systemStateVersion) {
        this.nodeInfo = nodeInfo;
        this.systemStateVersion = systemStateVersion;
    }

    public NodeInfo getNodeInfo() { return nodeInfo; }

    public int getSystemStateVersion() { return systemStateVersion; }

    public void setReply(Reply reply) { this.reply = reply; }

    public Reply getReply() { return reply; }

    public static class Reply {

        final int returnCode;
        final String returnMessage;

        public Reply() {
            this(0, null);
        }

        public Reply(int returnCode, String returnMessage) {
            this.returnCode = returnCode;
            this.returnMessage = returnMessage;
        }

        /** Returns whether this is an error response */
        public boolean isError() { return returnCode != 0; }

        /** Returns the return code, which is 0 if this request was successful */
        public int getReturnCode() { return returnCode; }

        /** Returns the message returned, or null if none */
        public String getReturnMessage() { return returnMessage; }

    }

}
