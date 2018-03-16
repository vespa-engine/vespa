// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2.requests;

import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;
import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTask;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Id;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Request;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Response;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.StateRestApiException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.DistributionState;

import java.util.stream.Collectors;

public class ClusterStateRequest extends Request<Response.ClusterResponse> {
    private final Id.Cluster id;
    private final int recursive;

    public ClusterStateRequest(Id.Cluster id, int recursive) {
        super(MasterState.MUST_BE_MASTER);
        this.id = id;
        this.recursive = recursive;
    }

    @Override
    public Response.ClusterResponse calculateResult(RemoteClusterControllerTask.Context context) throws StateRestApiException {
        Response.ClusterResponse result = new Response.ClusterResponse();
        result.addState("generated", new Response.UnitStateImpl(context.currentConsolidatedState.getClusterState()));
        for (NodeType type : NodeType.getTypes()) {
            Id.Service serviceId = new Id.Service(id, type);
            if (recursive > 0) {
                ServiceStateRequest ssr = new ServiceStateRequest(serviceId, recursive - 1);
                result.addEntry("service", type.toString(), ssr.calculateResult(context));
            } else {
                result.addLink("service", type.toString(), serviceId.toString());
            }
        }
        result.setPublishedState(bundleToDistributionState(context.publishedClusterStateBundle));
        return result;
    }

    private static DistributionState bundleToDistributionState(ClusterStateBundle bundle) {
        return new DistributionState(bundle.getBaselineClusterState().toString(),
                bundle.getDerivedBucketSpaceStates().entrySet().stream()
                        .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue().getClusterState().toString())));
    }
}
