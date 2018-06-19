// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * @author hakonhall
 */
public interface ClusterControllerJaxRsApi {

    @POST
    @Path("/cluster/v2/{clusterName}/storage/{storageNodeIndex}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ClusterControllerStateResponse setNodeState(
            @PathParam("clusterName") String clusterName,
            @PathParam("storageNodeIndex") int storageNodeIndex,
            @QueryParam("timeout") Float timeoutSeconds,
            ClusterControllerStateRequest request);

    @POST
    @Path("/cluster/v2/{clusterName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ClusterControllerStateResponse setClusterState(
            @PathParam("clusterName") String clusterName,
            @QueryParam("timeout") Float timeoutSeconds,
            ClusterControllerStateRequest request);

}
