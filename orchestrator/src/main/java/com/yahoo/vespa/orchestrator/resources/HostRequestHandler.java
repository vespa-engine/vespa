// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import ai.vespa.http.HttpURL.Path;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.Response;
import com.yahoo.restapi.JacksonJsonResponse;
import com.yahoo.restapi.RestApi;
import com.yahoo.restapi.RestApiException;
import com.yahoo.restapi.RestApiRequestHandler;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.Host;
import com.yahoo.vespa.orchestrator.HostNameNotFoundException;
import com.yahoo.vespa.orchestrator.OrchestrationException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostedVespaPolicy;
import com.yahoo.vespa.orchestrator.restapi.wire.GetHostResponse;
import com.yahoo.vespa.orchestrator.restapi.wire.HostService;
import com.yahoo.vespa.orchestrator.restapi.wire.HostStateChangeDenialReason;
import com.yahoo.vespa.orchestrator.restapi.wire.PatchHostRequest;
import com.yahoo.vespa.orchestrator.restapi.wire.PatchHostResponse;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import com.yahoo.vespa.orchestrator.status.HostStatus;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author oyving
 * @author bjorncs
 */
public class HostRequestHandler extends RestApiRequestHandler<HostRequestHandler> {

    private static final Logger log = Logger.getLogger(HostRequestHandler.class.getName());

    private final Orchestrator orchestrator;

    @Inject
    public HostRequestHandler(ThreadedHttpRequestHandler.Context context, Orchestrator orchestrator) {
        super(context, HostRequestHandler::createRestApiDefinition);
        this.orchestrator = orchestrator;
    }

    private static RestApi createRestApiDefinition(HostRequestHandler self) {
        return RestApi.builder()
                .addRoute(RestApi.route("/orchestrator/v1/hosts/{hostname}")
                        .get(self::getHost)
                        .patch(PatchHostRequest.class, self::patch))
                .addRoute(RestApi.route("/orchestrator/v1/hosts/{hostname}/suspended")
                        .put(self::suspend)
                        .delete(self::resume))
                .registerJacksonRequestEntity(PatchHostRequest.class)
                .registerJacksonResponseEntity(GetHostResponse.class)
                .registerJacksonResponseEntity(PatchHostResponse.class)
                .registerJacksonResponseEntity(UpdateHostResponse.class)
                .build();
    }

    /**
     * Shows the Orchestrator state of a host.
     */
    private GetHostResponse getHost(RestApi.RequestContext context) {
        String hostNameString = context.pathParameters().getStringOrThrow("hostname");
        HostName hostName = new HostName(hostNameString);
        try {
            Host host = orchestrator.getHost(hostName);

            URI applicationUri = context.baseRequestURL()
                    .withPath(Path.parse( "/orchestrator/v1/instances/" + host.getApplicationInstanceReference().asString()))
                    .asURI();

            List<HostService> hostServices = host.getServiceInstances().stream()
                    .map(serviceInstance -> new HostService(
                            serviceInstance.getServiceCluster().clusterId().s(),
                            serviceInstance.getServiceCluster().serviceType().s(),
                            serviceInstance.configId().s(),
                            serviceInstance.serviceStatus().name()))
                    .collect(Collectors.toList());

            return new GetHostResponse(
                    host.getHostName().s(),
                    host.getHostInfo().status().name(),
                    host.getHostInfo().suspendedSince().map(Instant::toString).orElse(null),
                    applicationUri.toString(),
                    hostServices);
        } catch (UncheckedTimeoutException e) {
            log.log(Level.FINE, () -> "Failed to get host " + hostName + ": " + e.getMessage());
            throw restApiExceptionFromTimeout("getHost", hostName, e);
        } catch (HostNameNotFoundException e) {
            log.log(Level.FINE, () -> "Host not found: " + hostName);
            throw new RestApiException.NotFound(e);
        }
    }

    /**
     * Tweak internal Orchestrator state for host.
     */
    private PatchHostResponse patch(RestApi.RequestContext context, PatchHostRequest request) {
        String hostNameString = context.pathParameters().getStringOrThrow("hostname");
        HostName hostName = new HostName(hostNameString);

        if (request.state != null) {
            HostStatus state;
            try {
                state = HostStatus.valueOf(request.state);
            } catch (IllegalArgumentException dummy) {
                throw new RestApiException.BadRequest("Bad state in request: '" + request.state + "'");
            }

            try {
                orchestrator.setNodeStatus(hostName, state);
            } catch (HostNameNotFoundException e) {
                log.log(Level.FINE, () -> "Host not found: " + hostName);
                throw new RestApiException.NotFound(e);
            } catch (UncheckedTimeoutException e) {
                log.log(Level.FINE, () -> "Failed to patch " + hostName + ": " + e.getMessage());
                throw restApiExceptionFromTimeout("patch", hostName, e);
            } catch (OrchestrationException e) {
                String message = "Failed to set " + hostName + " to " + state + ": " + e.getMessage();
                log.log(Level.FINE, message, e);
                throw new RestApiException.InternalServerError(message);
            }
        }

        PatchHostResponse response = new PatchHostResponse();
        response.description = "ok";
        return response;
    }

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
     */
    private UpdateHostResponse suspend(RestApi.RequestContext context) {
        String hostNameString = context.pathParameters().getStringOrThrow("hostname");
        HostName hostName = new HostName(hostNameString);
        try {
            orchestrator.suspend(hostName);
        } catch (HostNameNotFoundException e) {
            log.log(Level.FINE, () -> "Host not found: " + hostName);
            throw new RestApiException.NotFound(e);
        } catch (UncheckedTimeoutException e) {
            log.log(Level.FINE, () -> "Failed to suspend " + hostName + ": " + e.getMessage());
            throw restApiExceptionFromTimeout("suspend", hostName, e);
        } catch (HostStateChangeDeniedException e) {
            log.log(Level.FINE, () -> "Failed to suspend " + hostName + ": " + e.getMessage());
            throw restApiExceptionWithDenialReason("suspend", hostName, e);
        }
        return new UpdateHostResponse(hostName.s(), null);
    }
    /**
     * Resume normal operations for all services on a host that has previously been allowed suspension.
     *
     * If the host is already registered as running normal operations, then resume() is idempotent
     * and will succeed.
     */
    private UpdateHostResponse resume(RestApi.RequestContext context) {
        String hostNameString = context.pathParameters().getStringOrThrow("hostname");
        HostName hostName = new HostName(hostNameString);
        try {
            orchestrator.resume(hostName);
        } catch (HostNameNotFoundException e) {
            log.log(Level.FINE, () -> "Host not found: " + hostName);
            throw new RestApiException.NotFound(e);
        } catch (UncheckedTimeoutException e) {
            log.log(Level.FINE, () -> "Failed to resume " + hostName + ": " + e.getMessage());
            throw restApiExceptionFromTimeout("resume", hostName, e);
        } catch (HostStateChangeDeniedException e) {
            log.log(Level.FINE, () -> "Failed to resume " + hostName + ": " + e.getMessage());
            throw restApiExceptionWithDenialReason("resume", hostName, e);
        }
        return new UpdateHostResponse(hostName.s(), null);
    }

    private RestApiException restApiExceptionFromTimeout(String operationDescription,
                                                                HostName hostName,
                                                                UncheckedTimeoutException e) {
        // Return timeouts as 409 Conflict instead of 504 Gateway Timeout to reduce noise in 5xx graphs.
        return createRestApiException(operationDescription, hostName, e,
                HostedVespaPolicy.DEADLINE_CONSTRAINT, e.getMessage(), Response.Status.CONFLICT);
    }

    private RestApiException restApiExceptionWithDenialReason(
            String operationDescription,
            HostName hostName,
            HostStateChangeDeniedException e) {
        return createRestApiException(operationDescription, hostName, e, e.getConstraintName(), e.getMessage(),
                Response.Status.CONFLICT);
    }

    private RestApiException createRestApiException(
            String operationDescription, HostName hostname, Exception e, String constraint, String message, int status) {
        HostStateChangeDenialReason hostStateChangeDenialReason = new HostStateChangeDenialReason(
                constraint, operationDescription + " failed: " + message);
        UpdateHostResponse response = new UpdateHostResponse(hostname.s(), hostStateChangeDenialReason);
        return new RestApiException(
                new JacksonJsonResponse<>(status, response, restApi().jacksonJsonMapper(), true),
                hostStateChangeDenialReason.toString(),
                e);
    }
}
