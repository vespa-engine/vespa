// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.cost.restapi;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * Cost and Utilization APi for hosted Vespa.
 *
 * Used to give insight to PEG and application owners about
 * TOC and if the application is reasonable scaled.
 *
 * @author smorgrav
 */
@Path("v1")
@Produces(MediaType.APPLICATION_JSON)
public interface CostResource {

    @GET
    @Path("/analysis/cpu")
    List<CostJsonModel.Application> getCPUAnalysis();

    @GET
    @Produces("text/csv")
    @Path("/csv")
    String getCSV();

    @GET
    @Path("/apps")
    List<CostJsonModel.Application> getApplicationsCost();

    @GET
    @Path("/apps/{environment}/{region}/{application}")
    CostJsonModel.Application getApplicationCost(@PathParam("application") String appName,
                                                        @PathParam("region") String regionName,
                                                        @PathParam("environment") String envName);
}
