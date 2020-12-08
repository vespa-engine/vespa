// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.flags.json.wire.WireFlagData;
import com.yahoo.vespa.flags.json.wire.WireFlagDataList;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/**
 * @author hakonhall
 */
@Path("")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface FlagsV1Api {
    @PUT
    @Path("/data/{flagId}")
    void putFlagData(@PathParam("flagId") String flagId, @QueryParam("force") Boolean force, WireFlagData flagData);

    @GET
    @Path("/data/{flagId}")
    WireFlagData getFlagData(@PathParam("flagId") String flagId, @QueryParam("force") Boolean force);

    @DELETE
    @Path("/data/{flagId}")
    void deleteFlagData(@PathParam("flagId") String flagId, @QueryParam("force") Boolean force);

    @GET
    @Path("/data")
    WireFlagDataList listFlagData(@QueryParam("recursive") Boolean recursive);

    @GET
    @Path("/defined")
    Map<String, WireFlagDefinition> listFlagDefinition();

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    class WireFlagDefinition {
        @JsonProperty("owners") public List<String> owners;
        @JsonProperty("expiresAt") public String expiresAt;
    }
}
