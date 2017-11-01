// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.Host;
import com.yahoo.vespa.orchestrator.HostNameNotFoundException;
import com.yahoo.vespa.orchestrator.OrchestrationException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
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

            // TODO: Use applicationUri.toString() and hostServices once nobody is using <6.164
            // TODO: Enable testing again in HostResourceTest.getHost_works
            return new GetHostResponse(
                    host.getHostName().s(),
                    host.getHostStatus().name(),
                    null,
                    null);
        } catch (HostNameNotFoundException e) {
            log.log(LogLevel.INFO, "Host not found: " + hostName);
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
                log.log(LogLevel.INFO, "Host not found: " + hostName);
                throw new NotFoundException(e);
            } catch (OrchestrationException e) {
                String message = "Failed to set " + hostName + " to " + state + ": " + e.getMessage();
                log.log(LogLevel.INFO, message, e);
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
            log.log(LogLevel.INFO, "Host not found: " + hostName);
            throw new NotFoundException(e);
        } catch (HostStateChangeDeniedException e) {
            log.log(LogLevel.INFO, "Failed to suspend " + hostName + ": " + e.getMessage());
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
            log.log(LogLevel.INFO, "Host not found: " + hostName);
            throw new NotFoundException(e);
        } catch (HostStateChangeDeniedException e) {
            log.log(LogLevel.INFO, "Failed to resume " + hostName + ": " + e.getMessage());
            throw webExceptionWithDenialReason("resume", hostName, e);
        }
        return new UpdateHostResponse(hostName.s(), null);
    }

    private static WebApplicationException webExceptionWithDenialReason(
            String operationDescription,
            HostName hostName,
            HostStateChangeDeniedException e) {
        HostStateChangeDenialReason hostStateChangeDenialReason =
                new HostStateChangeDenialReason(
                        e.getConstraintName(),
                        operationDescription + " failed: " + e.getMessage());
        UpdateHostResponse response = new UpdateHostResponse(hostName.s(), hostStateChangeDenialReason);
        return new WebApplicationException(
                hostStateChangeDenialReason.toString(),
                e,
                Response.status(Response.Status.CONFLICT)
                        .entity(response)
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());

    }

}

