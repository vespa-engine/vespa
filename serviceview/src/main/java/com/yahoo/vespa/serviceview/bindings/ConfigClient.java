// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview.bindings;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Client to fetch the model config from the configserver.
 *
 * @author Steinar Knutsen
 */
public interface ConfigClient {

    @GET
    @Path("/config/v2/tenant/{tenantName}/application/{applicationName}/environment/{environmentName}/region/{regionName}/instance/{instanceName}/cloud.config.model")
    @Produces(MediaType.APPLICATION_JSON)
    ModelResponse getServiceModel(@PathParam("tenantName") String tenantName,
                                  @PathParam("applicationName") String applicationName,
                                  @PathParam("environmentName") String environmentName,
                                  @PathParam("regionName") String regionName,
                                  @PathParam("instanceName") String instanceName);
}
