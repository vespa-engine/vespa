// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

public abstract class SetClusterStateRequest extends ClusterStateVersionSpecificRequest {

    public SetClusterStateRequest(NodeInfo nodeInfo, int clusterStateVersion) {
        super(nodeInfo, clusterStateVersion);
    }

}
