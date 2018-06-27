// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.statuspage;

import com.fasterxml.jackson.databind.JsonNode;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * @author andreer
 */
@Path("/v1/")
@Produces(MediaType.APPLICATION_JSON)
public interface StatusPageResource {

    @GET
    @Path("{page}")
    @Produces(MediaType.APPLICATION_JSON)
    JsonNode statusPage(@PathParam("page") String page, @QueryParam("since") String since);
}
