// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.EmptyResponse;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.http.HttpResponse.Status;
import com.yahoo.restapi.RestApi;
import com.yahoo.restapi.RestApiException;
import com.yahoo.restapi.RestApiRequestHandler;
import com.yahoo.vespa.orchestrator.ApplicationIdNotFoundException;
import com.yahoo.vespa.orchestrator.ApplicationStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author smorgrav
 * @author bjorncs
 */
public class ApplicationSuspensionRequestHandler extends RestApiRequestHandler<ApplicationSuspensionRequestHandler> {

    private static final Logger log = Logger.getLogger(ApplicationSuspensionRequestHandler.class.getName());

    private final Orchestrator orchestrator;

    @Inject
    public ApplicationSuspensionRequestHandler(ThreadedHttpRequestHandler.Context context, Orchestrator orchestrator) {
        super(context, ApplicationSuspensionRequestHandler::createRestApiDefinition);
        this.orchestrator = orchestrator;
    }

    private static RestApi createRestApiDefinition(ApplicationSuspensionRequestHandler self) {
        return RestApi.builder()
                .addRoute(RestApi.route("/orchestrator/v1/suspensions/applications")
                        .get(self::getApplications)
                        .post(String.class, self::suspend))
                .addRoute(RestApi.route("/orchestrator/v1/suspensions/applications/{application}")
                        .get(self::getApplication)
                        .delete(self::resume))
                .registerJacksonResponseEntity(Set.class)
                .build();
    }

    /**
     * Lists all applications that is currently suspended.
     *
     * HTTP Behavior:
     * Always 200
     *
     * @return A list of application ids of suspended applications
     */
    private Set<String> getApplications(RestApi.RequestContext context) {
        Set<ApplicationId> refs = orchestrator.getAllSuspendedApplications();
        return refs.stream().map(ApplicationId::serializedForm).collect(Collectors.toSet());
    }

    /**
     * Shows the Orchestrator status for an application instance
     *
     * HTTP Behavior:
     * 204 if the application is suspended
     * 400 if the applicationId is invalid
     * 404 if the application is not suspended
     */
    private HttpResponse getApplication(RestApi.RequestContext context) {
        String applicationIdString = context.pathParameters().getStringOrThrow("application");
        ApplicationId appId = toApplicationId(applicationIdString);
        ApplicationInstanceStatus status;

        try {
            status = orchestrator.getApplicationInstanceStatus(appId);
        } catch (ApplicationIdNotFoundException e) {
            throw new RestApiException.NotFound("Application " + applicationIdString + " could not be found", e);
        }

        if (status.equals(ApplicationInstanceStatus.NO_REMARKS)) {
            throw new RestApiException.NotFound("Application " + applicationIdString + " is not suspended");
        }
        return new EmptyResponse(Status.NO_CONTENT);
    }

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
     */
    private HttpResponse suspend(RestApi.RequestContext context, String applicationIdString) {
        ApplicationId applicationId = toApplicationId(applicationIdString);
        try {
            orchestrator.suspend(applicationId);
        } catch (ApplicationIdNotFoundException e) {
            log.log(Level.INFO, "ApplicationId " + applicationIdString + " not found.", e);
            throw new RestApiException.NotFound(e);
        } catch (ApplicationStateChangeDeniedException e) {
            log.log(Level.INFO, "Suspend for " + applicationIdString + " failed.", e);
            throw new RestApiException.Conflict();
        } catch (RuntimeException e) {
            log.log(Level.INFO, "Suspend for " + applicationIdString + " failed from unknown reasons", e);
            throw new RestApiException.InternalServerError(e);
        }
        return new EmptyResponse(Status.NO_CONTENT);
    }

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
     */
    private HttpResponse resume(RestApi.RequestContext context) {
        String applicationIdString = context.pathParameters().getStringOrThrow("application");
        ApplicationId applicationId = toApplicationId(applicationIdString);
        try {
            orchestrator.resume(applicationId);
        } catch (ApplicationIdNotFoundException e) {
            log.log(Level.INFO, "ApplicationId " + applicationIdString + " not found.", e);
            throw new RestApiException.NotFound(e);
        } catch (ApplicationStateChangeDeniedException e) {
            log.log(Level.INFO, "Suspend for " + applicationIdString + " failed.", e);
            throw new RestApiException.Conflict();
        } catch (RuntimeException e) {
            log.log(Level.INFO, "Suspend for " + applicationIdString + " failed from unknown reasons", e);
            throw new RestApiException.InternalServerError(e);
        }
        return new EmptyResponse(Status.NO_CONTENT);
    }

    private ApplicationId toApplicationId(String applicationIdString) {
        try {
            return ApplicationId.fromSerializedForm(applicationIdString);
        } catch (IllegalArgumentException e) {
            throw new RestApiException.BadRequest(e);
        }
    }

}
