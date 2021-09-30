// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2.requests;

import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTask;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Id;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Request;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Response;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.StateRestApiException;

public class ServiceStateRequest extends Request<Response.ServiceResponse> {
    private final Id.Service id;
    private final int recursive;

    public ServiceStateRequest(Id.Service id, int recursive) {
        super(MasterState.MUST_BE_MASTER);
        this.id = id;
        this.recursive = recursive;
    }

    @Override
    public Response.ServiceResponse calculateResult(RemoteClusterControllerTask.Context context) throws StateRestApiException {
        Response.ServiceResponse result = new Response.ServiceResponse();
        for (Integer i : context.cluster.getConfiguredNodes().keySet()) {
            Id.Node nodeId = new Id.Node(id, i);
            if (recursive > 0) {
                // Don't include per-node statistics when aggregating over all nodes
                NodeStateRequest nsr = new NodeStateRequest(nodeId);
                result.addEntry("node", String.valueOf(i), nsr.calculateResult(context));
            } else {
                result.addLink("node", String.valueOf(i), nodeId.toString());
            }
        }
        return result;
    }
}
