// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.restapi;

import com.yahoo.vespa.jaxrs.annotation.PATCH;
import com.yahoo.vespa.orchestrator.restapi.wire.GetHostResponse;
import com.yahoo.vespa.orchestrator.restapi.wire.PatchHostRequest;
import com.yahoo.vespa.orchestrator.restapi.wire.PatchHostResponse;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Definition of Orchestrator's REST API for hosts.
 *
 * Implementing classes must not put any JAX-RS annotation on the overridden methods. Doing so will cause all
 * method annotations in this interface to be ignored by the JAX-RS container (see section 3.6 of JSR-339).
 *
 * @author bakksjo
 */
public interface HostApi {
    /**
     * Path prefix for this api. Resources implementing this API should use this with a @Path annotation.
     */
    String PATH_PREFIX = "/v1/hosts";

    /**
     * Shows the Orchestrator state of a host.
     * 
     * @param hostNameString the fully qualified host name
     */
    @GET
    @Path("/{hostname}")
    @Produces(MediaType.APPLICATION_JSON)
    GetHostResponse getHost(@PathParam("hostname") String hostNameString);

    /**
     * Tweak internal Orchestrator state for host.
     */
    @PATCH
    @Path("/{hostname}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    PatchHostResponse patch(@PathParam("hostname") String hostNameString, PatchHostRequest request);

    /**
     * Ask for permission to temporarily suspend all services on a host.
     *
     * On success, none, some, or all services on the host may already have been effectively suspended,
     * e.g. as of Feb 2015, a content node would already be set in the maintenance state.
     *
     * Once the host is ready to resume normal operations, it must finish with resume() (see below).
     *
     * If the host has already been granted permission to suspend all services, requesting
     * suspension again is idempotent and will succeed.
     *
     * @param hostNameString the fully qualified host name.
     */
    @PUT
    @Path("/{hostname}/suspended")
    @Produces(MediaType.APPLICATION_JSON)
    UpdateHostResponse suspend(@PathParam("hostname") String hostNameString);

    /**
     * Resume normal operations for all services on a host that has previously been allowed suspension.
     *
     * If the host is already registered as running normal operations, then resume() is idempotent
     * and will succeed.
     *
     * @param hostNameString the fully qualified host name.
     */
    @DELETE
    @Path("/{hostname}/suspended")
    @Produces(MediaType.APPLICATION_JSON)
    UpdateHostResponse resume(@PathParam("hostname") String hostNameString);
}
