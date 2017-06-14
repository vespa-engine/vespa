// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview.bindings;

import java.util.HashMap;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

public interface StateClient {

    @GET
    @Path("v1/")
    @Produces(MediaType.APPLICATION_JSON)
    public ApplicationView getDefaultUserInfo();

    @GET
    @Path("v1/tenant/{tenantName}/application/{applicationName}/environment/{environmentName}/region/{regionName}/instance/{instanceName}")
    @Produces(MediaType.APPLICATION_JSON)
    public ApplicationView getUserInfo(@PathParam("tenantName") String tenantName,
            @PathParam("applicationName") String applicationName,
            @PathParam("environmentName") String environmentName,
            @PathParam("regionName") String regionName,
            @PathParam("instanceName") String instanceName);

    @SuppressWarnings("rawtypes")
    @GET
    @Path("v1/tenant/{tenantName}/application/{applicationName}/environment/{environmentName}/region/{regionName}/instance/{instanceName}/service/{serviceIdentifier}/{apiParams: .*}")
    @Produces(MediaType.APPLICATION_JSON)
    public HashMap singleService(@PathParam("tenantName") String tenantName,
            @PathParam("applicationName") String applicationName,
            @PathParam("environmentName") String environmentName,
            @PathParam("regionName") String regionName,
            @PathParam("instanceName") String instanceName,
            @PathParam("serviceIdentifier") String identifier,
            @PathParam("apiParams") String apiParams);
}
