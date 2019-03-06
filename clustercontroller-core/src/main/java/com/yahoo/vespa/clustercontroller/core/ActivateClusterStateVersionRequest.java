// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

public class ActivateClusterStateVersionRequest extends ClusterStateVersionSpecificRequest {

    public ActivateClusterStateVersionRequest(NodeInfo nodeInfo, int systemStateVersion) {
        super(nodeInfo, systemStateVersion);
    }

}
