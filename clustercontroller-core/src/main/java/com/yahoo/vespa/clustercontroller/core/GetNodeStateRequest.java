// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * Represents an abstract request sent from the fleet controller to the controlled nodes to get state.
 */
public abstract class GetNodeStateRequest {

    private final NodeInfo nodeInfo;
    private Reply reply;

    public GetNodeStateRequest(NodeInfo nodeInfo) {
        this.nodeInfo = nodeInfo;
    }

    public Reply getReply() {
        return reply;
    }

    /** Called when the reply to this request becomes available. */
    // TODO: The request shouldn't have this, of course
    public void setReply(Reply reply) { this.reply = reply; }

    public NodeInfo getNodeInfo() { return nodeInfo; }

    public abstract void abort();

    public static class Reply {

        private final int returnCode;
        private final String returnMessage;
        private final String stateString;
        private final String hostInfo;

        /** Create a failure reply */
        public Reply(int returnCode, String errorMessage) {
            this.returnCode = returnCode;
            this.returnMessage = errorMessage;
            this.stateString = null;
            this.hostInfo = null;
        }

        /** Create a successful reply */
        public Reply(String stateString, String hostInfo) {
            this.returnCode = 0;
            this.returnMessage = null;
            this.stateString = stateString;
            this.hostInfo = hostInfo;
        }

        /** Returns the return code, which is 0 on success */
        public int getReturnCode() { return returnCode; }

        /** Returns the returned error message, or null on success */
        public String getReturnMessage() { return returnMessage; }

        /** Returns the state string, or null if this request failed */
        public String getStateString() { return stateString; }

        /** Returns the host info, or null if this request failed */
        public String getHostInfo() { return hostInfo; }

        /** Returns whether this request failed */
        public boolean isError() { return returnCode != 0; }

    }

}
