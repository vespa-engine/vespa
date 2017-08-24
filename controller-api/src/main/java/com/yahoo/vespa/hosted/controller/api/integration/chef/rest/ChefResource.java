// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.chef.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * @author mortent
 * @author mpolden
 */

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ChefResource {

    @Path("/organizations/{organization}/environments/{environment}/nodes")
    @Consumes("application/json")
    @GET
    List<String> getNodes(@PathParam("organization") String organization, @PathParam("environment") String environment);

    @GET
    @Path("/organizations/{organization}/nodes/{nodename}")
    ChefNode getNode(@PathParam("organization") String organization, @PathParam("nodename") String nodename);

    @PUT
    @Path("/organizations/{organization}/nodes/{nodename}")
    ChefNode updateNode(@PathParam("organization") String organization, @PathParam("nodename") String nodeName, String node);

    @DELETE
    @Path("/organizations/{organization}/nodes/{nodename}")
    ChefNode deleteNode(@PathParam("organization") String organization, @PathParam("nodename") String nodeName);

    @GET
    @Path("/organizations/{organization}/clients/{name}")
    Client getClient(@PathParam("organization") String organization, @PathParam("name") String name);

    @DELETE
    @Path("/organizations/{organization}/clients/{name}")
    Client deleteClient(@PathParam("organization") String organization, @PathParam("name") String name);

    @GET
    @Path("/organizations/{organization}/environments/{environment}")
    ChefEnvironment getEnvironment(@PathParam("organization") String organization, @PathParam("environment") String environment);

    @PUT
    @Path("/organizations/{organization}/environments/{name}")
    String updateEnvironment(@PathParam("organization") String organization, @PathParam("name") String chefEnvironmentName, String contentAsString);

    @POST
    @Path("/organizations/{organization}/environments")
    String createEnvironment(@PathParam("organization") String organization, String contentAsString);

    @GET
    @Path("/organizations/{organization}/search/node")
    NodeResult searchNode(@PathParam("organization") String organization, @QueryParam("q") String query);

    @POST
    @Path("/organizations/{organization}/search/node")
    PartialNodeResult partialSearchNode(@PathParam("organization") String organization, @QueryParam("q") String query, @QueryParam("rows") int rows, String keys);

    @GET
    @Path("/organizations/{organization}/cookbooks/{name}/{version}")
    CookBook getCookBook(@PathParam("organization") String organization, @PathParam("name") String name, @PathParam("version") String version);
}
