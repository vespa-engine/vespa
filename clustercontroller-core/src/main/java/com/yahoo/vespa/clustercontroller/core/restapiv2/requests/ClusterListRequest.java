// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2.requests;

import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTask;
import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTaskScheduler;
import com.yahoo.vespa.clustercontroller.core.restapiv2.*;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.StateRestApiException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;

import java.util.Map;

public class ClusterListRequest extends Request<UnitResponse> {
    private final int recursive;
    private final ClusterControllerStateRestAPI.FleetControllerResolver resolver;

    public ClusterListRequest(int recursive,
                              ClusterControllerStateRestAPI.FleetControllerResolver resolver)
    {
        super(MasterState.NEED_NOT_BE_MASTER);
        this.recursive = recursive;
        this.resolver = resolver;
    }

    @Override
    public UnitResponse calculateResult(RemoteClusterControllerTask.Context context) throws StateRestApiException, OtherMasterIndexException {
        return calculateResult();
    }

    /**
     * The cluster list request is outside of the fleet controllers, and can thus not use a
     * context (thus it is null all the time). Thus it must recurse into fleetcontrollers if
     * needed. Adding function without context to make this obvious and hinder bad usage.
     */
    private UnitResponse calculateResult() throws StateRestApiException, OtherMasterIndexException {
        Response.ClusterListResponse response = new Response.ClusterListResponse();
        for (Map.Entry<String, RemoteClusterControllerTaskScheduler> e : resolver.getFleetControllers().entrySet()) {
            Id.Cluster clusterId = new Id.Cluster(e.getKey());
            if (recursive > 0) {
                ClusterStateRequest csr = new ClusterStateRequest(clusterId, recursive - 1);
                e.getValue().schedule(csr);
                csr.waitForCompletion();
                response.addEntry("cluster", clusterId.getClusterId(), csr.getResult());
            } else {
                response.addLink("cluster", clusterId.getClusterId(), clusterId.toString());
            }
        }
        return response;
    }
}
