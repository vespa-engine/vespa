// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4;

import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ApplicationReference;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.InstancesReply;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * @author gv
 */
@Path("") //Ensures that the produces annotation is inherited
@Produces(MediaType.APPLICATION_JSON)
public interface ApplicationResource {

    String API_PATH = "application";

    @GET
    List<ApplicationReference> listApplications();

    @Path("{applicationId}")
    @POST
    ApplicationReference createApplication(@PathParam("applicationId") ApplicationId applicationId);

    @Path("{applicationId}")
    @DELETE
    void deleteApplication(@PathParam("applicationId") ApplicationId applicationId);

    @Path("{applicationId}/environment")
    EnvironmentResource environment();

    @Path("{applicationId}")
    @GET
    InstancesReply listInstances(@PathParam("applicationId") ApplicationId applicationId);
    
}
