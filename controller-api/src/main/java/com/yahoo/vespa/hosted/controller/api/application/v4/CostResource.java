// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4;

import com.yahoo.vespa.hosted.controller.api.application.v4.model.CostMonths;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.CostResult;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * @author ogronnesby
 */
@Path("")  //Ensures that the produces annotation is inherited
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface CostResource {
    String API_PATH = "cost";

    @GET
    @Path("/")
    CostMonths costMonths();

    @GET
    @Path("/{month}")
    CostResult cost(@PathParam("month") String month);
}
