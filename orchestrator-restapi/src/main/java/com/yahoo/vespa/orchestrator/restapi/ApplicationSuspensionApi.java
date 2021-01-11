// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.restapi;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Set;

/**
 * Definition of Orchestrator's REST API for suspensions of applications aka application instances.
 *
 * Implementing classes must not put any JAX-RS annotation on the overridden methods. Doing so will cause all
 * method annotations in this interface to be ignored by the JAX-RS container (see section 3.6 of JSR-339).
 *
 * @author smorgrav
 */
@Path("/orchestrator" + ApplicationSuspensionApi.PATH_PREFIX)
public interface ApplicationSuspensionApi {
    /**
     * Path prefix for this api. Resources implementing this API should use this with a @Path annotation.
     */
    String PATH_PREFIX = "/v1/suspensions/applications";

    /**
     * Lists all applications that is currently suspended.
     *
     * HTTP Behavior:
     * Always 200
     *
     * @return A list of application ids of suspended applications
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Set<String> getApplications();

    /**
     * Shows the Orchestrator status for an application instance
     *
     * HTTP Behavior:
     * 204 if the application is suspended
     * 400 if the applicationId is invalid
     * 404 if the application is not suspended
     *
     * @param applicationIdString the fully qualified application id.
     */
    @GET
    @Path("/{application}")
    @Produces(MediaType.APPLICATION_JSON)
    void getApplication(@PathParam("application") String applicationIdString);

    /**
     * Ask for permission to temporarily suspend all services for an application instance.
     *
     * On success all content nodes for this application instance have been set in maintenance mode.
     *
     * Once the application is ready to resume normal operations, it must finish with resume() (see below).
     *
     * If the application has already been granted permission to suspend all services, requesting
     * suspension again is idempotent and will succeed.
     *
     * HTTP Behavior:
     * 204 is the suspend operation was successful
     * 400 if the applicationId is invalid
     * 409 if the suspend was denied
     *
     * @param applicationIdString the fully qualified application id.
     */
    @POST
    void suspend(String applicationIdString);

    /**
     * Resume normal operations for all services for an application
     * instance that has previously been allowed suspension.
     *
     * If the host is already registered as running normal operations, then resume() is idempotent
     * and will succeed.
     *
     * HTTP Behavior:
     * Returns 204 is the resume operation was successful (or the application was not suspended)
     * Returns 400 if the applicationId is invalid
     *
     * @param applicationIdString the fully qualified application id.
     */
    @DELETE
    @Path("/{application}")
    void resume(@PathParam("application") String applicationIdString);
}
