// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.zone.v1;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * Used by build system and command-line tool.
 *
 * @author smorgrav
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path(ZoneApi.API_VERSION)
public interface ZoneApi {

    String API_VERSION = "v1";

    @GET
    @Path("")
    List<ZoneReference.Environment> listEnvironments();

    @GET
    @Path("/environment/{environment}")
    List<ZoneReference.Region> listRegions(@PathParam("environment") String env);

    @GET
    @Path("/environment/{environment}/default")
    ZoneReference.Region defaultRegion(@PathParam("environment") String env);
}
