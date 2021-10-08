// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview.bindings;

import java.util.HashMap;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Client to fetch the model config from the configserver.
 *
 * @author Steinar Knutsen
 */
public interface HealthClient {

    @SuppressWarnings("rawtypes")
    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public HashMap getHealthInfo();
}
