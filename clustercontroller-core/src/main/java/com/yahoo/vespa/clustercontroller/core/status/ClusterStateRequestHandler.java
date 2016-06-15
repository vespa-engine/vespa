// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageResponse;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageServer;
import com.yahoo.vespa.clustercontroller.core.SystemStateGenerator;

public class ClusterStateRequestHandler implements StatusPageServer.RequestHandler  {
    private final SystemStateGenerator systemStateGenerator;

    public ClusterStateRequestHandler(SystemStateGenerator systemStateGenerator) {
        this.systemStateGenerator = systemStateGenerator;
    }
    @Override
    public StatusPageResponse handle(StatusPageServer.HttpRequest request) {
        ClusterState cs = systemStateGenerator.getClusterState();

        StatusPageResponse response = new StatusPageResponse();
        response.setContentType("text/plain");
        response.writeContent(cs.toString());
        return response;
    }
}
