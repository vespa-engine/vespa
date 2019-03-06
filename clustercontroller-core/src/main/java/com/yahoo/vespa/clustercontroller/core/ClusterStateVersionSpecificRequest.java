// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

public abstract class ClusterStateVersionSpecificRequest {

    private final NodeInfo nodeInfo;
    private final int clusterStateVersion;
    private Reply reply;

    public ClusterStateVersionSpecificRequest(NodeInfo nodeInfo, int clusterStateVersion) {
        this.nodeInfo = nodeInfo;
        this.clusterStateVersion = clusterStateVersion;
    }

    public NodeInfo getNodeInfo() { return nodeInfo; }

    public int getClusterStateVersion() { return clusterStateVersion; }

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
