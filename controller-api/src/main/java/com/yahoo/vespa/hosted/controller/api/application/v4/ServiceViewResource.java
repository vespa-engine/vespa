// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4;

import com.yahoo.vespa.serviceview.bindings.ApplicationView;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;

/**
 * @author Stian Kristoffersen
 */
@Path("")
@Produces(MediaType.APPLICATION_JSON)
public interface ServiceViewResource {

    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    ApplicationView getUserInfo();

    @GET
    @Path("{serviceIdentifier}/{apiParams: .*}")
    @Produces(MediaType.APPLICATION_JSON)
    @SuppressWarnings("rawtypes")
    HashMap singleService(@PathParam("serviceIdentifier") String identifier,
                          @PathParam("apiParams") String apiParams);

}
