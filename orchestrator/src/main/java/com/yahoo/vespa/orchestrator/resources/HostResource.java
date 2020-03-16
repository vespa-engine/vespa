// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.Host;
import com.yahoo.vespa.orchestrator.HostNameNotFoundException;
import com.yahoo.vespa.orchestrator.OrchestrationException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostedVespaPolicy;
import com.yahoo.vespa.orchestrator.restapi.HostApi;
import com.yahoo.vespa.orchestrator.restapi.wire.GetHostResponse;
import com.yahoo.vespa.orchestrator.restapi.wire.HostService;
import com.yahoo.vespa.orchestrator.restapi.wire.HostStateChangeDenialReason;
import com.yahoo.vespa.orchestrator.restapi.wire.PatchHostRequest;
import com.yahoo.vespa.orchestrator.restapi.wire.PatchHostResponse;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import com.yahoo.vespa.orchestrator.status.HostStatus;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author oyving
 */
@Path(HostApi.PATH_PREFIX)
public class HostResource implements HostApi {
    private static final Logger log = Logger.getLogger(HostResource.class.getName());

    private final Orchestrator orchestrator;
    private final UriInfo uriInfo;

    @Inject
    public HostResource(@Component Orchestrator orchestrator, @Context UriInfo uriInfo) {
        this.orchestrator = orchestrator;
        this.uriInfo = uriInfo;
    }

    @Override
    public GetHostResponse getHost(String hostNameString) {
        HostName hostName = new HostName(hostNameString);
        try {
            Host host = orchestrator.getHost(hostName);

            URI applicationUri = uriInfo.getBaseUriBuilder()
                    .path(InstanceResource.class)
                    .path(host.getApplicationInstanceReference().asString())
                    .build();

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
            log.log(LogLevel.DEBUG, "Failed to get host " + hostName + ": " + e.getMessage());
            throw webExceptionFromTimeout("getHost", hostName, e);
        } catch (HostNameNotFoundException e) {
            log.log(LogLevel.DEBUG, "Host not found: " + hostName);
            throw new NotFoundException(e);
        }
    }

    @Override
    public PatchHostResponse patch(String hostNameString, PatchHostRequest request) {
        HostName hostName = new HostName(hostNameString);

        if (request.state != null) {
            HostStatus state;
            try {
                state = HostStatus.valueOf(request.state);
            } catch (IllegalArgumentException dummy) {
                throw new BadRequestException("Bad state in request: '" + request.state + "'");
            }

            try {
                orchestrator.setNodeStatus(hostName, state);
            } catch (HostNameNotFoundException e) {
                log.log(LogLevel.DEBUG, "Host not found: " + hostName);
                throw new NotFoundException(e);
            } catch (UncheckedTimeoutException e) {
                log.log(LogLevel.DEBUG, "Failed to patch " + hostName + ": " + e.getMessage());
                throw webExceptionFromTimeout("patch", hostName, e);
            } catch (OrchestrationException e) {
                String message = "Failed to set " + hostName + " to " + state + ": " + e.getMessage();
                log.log(LogLevel.DEBUG, message, e);
                throw new InternalServerErrorException(message);
            }
        }

        PatchHostResponse response = new PatchHostResponse();
        response.description = "ok";
        return response;
    }

    @Override
    public UpdateHostResponse suspend(String hostNameString) {
        HostName hostName = new HostName(hostNameString);
        try {
            orchestrator.suspend(hostName);
        } catch (HostNameNotFoundException e) {
            log.log(LogLevel.DEBUG, "Host not found: " + hostName);
            throw new NotFoundException(e);
        } catch (UncheckedTimeoutException e) {
            log.log(LogLevel.DEBUG, "Failed to suspend " + hostName + ": " + e.getMessage());
            throw webExceptionFromTimeout("suspend", hostName, e);
        } catch (HostStateChangeDeniedException e) {
            log.log(LogLevel.DEBUG, "Failed to suspend " + hostName + ": " + e.getMessage());
            throw webExceptionWithDenialReason("suspend", hostName, e);
        }
        return new UpdateHostResponse(hostName.s(), null);
    }

    @Override
    public UpdateHostResponse resume(final String hostNameString) {
        HostName hostName = new HostName(hostNameString);
        try {
            orchestrator.resume(hostName);
        } catch (HostNameNotFoundException e) {
            log.log(LogLevel.DEBUG, "Host not found: " + hostName);
            throw new NotFoundException(e);
        } catch (UncheckedTimeoutException e) {
            log.log(LogLevel.DEBUG, "Failed to resume " + hostName + ": " + e.getMessage());
            throw webExceptionFromTimeout("resume", hostName, e);
        } catch (HostStateChangeDeniedException e) {
            log.log(LogLevel.DEBUG, "Failed to resume " + hostName + ": " + e.getMessage());
            throw webExceptionWithDenialReason("resume", hostName, e);
        }
        return new UpdateHostResponse(hostName.s(), null);
    }

    private static WebApplicationException webExceptionFromTimeout(String operationDescription,
                                                                   HostName hostName,
                                                                   UncheckedTimeoutException e) {
        // Return timeouts as 409 Conflict instead of 504 Gateway Timeout to reduce noise in 5xx graphs.
        return createWebException(operationDescription, hostName, e,
                HostedVespaPolicy.DEADLINE_CONSTRAINT, e.getMessage(), Response.Status.CONFLICT);
    }

    private static WebApplicationException webExceptionWithDenialReason(
            String operationDescription,
            HostName hostName,
            HostStateChangeDeniedException e) {
        return createWebException(operationDescription, hostName, e, e.getConstraintName(), e.getMessage(),
                Response.Status.CONFLICT);
    }

    private static WebApplicationException createWebException(String operationDescription,
                                                              HostName hostname,
                                                              Exception e,
                                                              String constraint,
                                                              String message,
                                                              Response.Status status) {
        HostStateChangeDenialReason hostStateChangeDenialReason = new HostStateChangeDenialReason(
                constraint, operationDescription + " failed: " + message);
        UpdateHostResponse response = new UpdateHostResponse(hostname.s(), hostStateChangeDenialReason);
        return new WebApplicationException(
                hostStateChangeDenialReason.toString(),
                e,
                Response.status(status)
                        .entity(response)
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }
}

