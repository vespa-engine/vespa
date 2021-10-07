// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4;

import com.yahoo.vespa.hosted.controller.api.application.v4.model.TenantInfo;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * @author gv
 */
@Path("/v4/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ApplicationApi {

    @GET
    @Path(TenantResource.API_PATH)
    List<TenantInfo> listTenants();

    @Path(TenantResource.API_PATH + "/{tenantId}")
    TenantResource tenant(@PathParam("tenantId")TenantId tenantId);

}
