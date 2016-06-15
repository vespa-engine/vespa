// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.noderepository.bindings;

import com.yahoo.vespa.jaxrs.annotation.PATCH;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * @author stiankri
 */
public interface NodeRepositoryApi {
    @GET
    @Path("/nodes/v2/node/")
    GetNodesResponse getNodesWithParentHost(
            @QueryParam("parentHost") String hostname,
            @QueryParam("recursive") boolean recursive);

    @PUT
    @Path("/nodes/v2/state/ready/{hostname}")
    // TODO: remove fake return String body; should be void and empty
    String setReady(@PathParam("hostname") String hostname, String body);

    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/nodes/v2/node/{nodename}")
    UpdateNodeAttributesResponse updateNodeAttributes(
            @PathParam("nodename") String hostname,
            UpdateNodeAttributesRequestBody body);
}
