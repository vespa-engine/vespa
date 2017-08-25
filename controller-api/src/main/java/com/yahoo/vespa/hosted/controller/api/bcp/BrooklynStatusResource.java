// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.bcp;

import com.fasterxml.jackson.databind.JsonNode;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * @author andreer
 */
@Path("") //Ensures that the produces annotation is inherited
@Produces(MediaType.APPLICATION_JSON)
public interface BrooklynStatusResource {

    @GET
    @Path("{rotation}")
    @Produces(MediaType.APPLICATION_JSON)
    JsonNode rotationStatus(@PathParam("rotation") String page);
}
