// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4;

import com.yahoo.vespa.hosted.controller.api.application.v4.model.TenantCreateOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.TenantInfo;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.TenantUpdateOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.TenantWithApplications;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * @author Tony Vaagenes
 */
@Path("")  //Ensures that the produces annotation is inherited
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface TenantResource {

    String API_PATH = "tenant";

    @GET
    TenantWithApplications metaData();

    @DELETE
    TenantInfo deleteTenant();

    @POST
    TenantInfo createTenant(TenantCreateOptions tenantOptions);

    @PUT
    TenantInfo updateTenant(TenantUpdateOptions tenantOptions);

    @Path(ApplicationResource.API_PATH)
    ApplicationResource application();

}
