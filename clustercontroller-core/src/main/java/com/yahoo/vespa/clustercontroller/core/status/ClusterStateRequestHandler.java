// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vespa.clustercontroller.core.StateVersionTracker;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageResponse;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageServer;

public class ClusterStateRequestHandler implements StatusPageServer.RequestHandler  {

    private final StateVersionTracker stateVersionTracker;

    public ClusterStateRequestHandler(StateVersionTracker stateVersionTracker) {
        this.stateVersionTracker = stateVersionTracker;
    }
    @Override
    public StatusPageResponse handle(StatusPageServer.HttpRequest request) {
        ClusterState cs = stateVersionTracker.getVersionedClusterState();

        StatusPageResponse response = new StatusPageResponse();
        response.setContentType("text/plain");
        response.writeContent(cs.toString());
        return response;
    }

    @Override
    public String pattern() { return "^/clusterstate"; }

}
