// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.orchestrator.HostNameNotFoundException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.restapi.wire.GetHostResponse;
import com.yahoo.vespa.orchestrator.restapi.HostApi;
import com.yahoo.vespa.orchestrator.restapi.wire.HostStateChangeDenialReason;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.applicationmodel.HostName;
import org.apache.commons.lang.exception.ExceptionUtils;

import java.util.logging.Logger;

import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author oyving
 */
@Path(HostApi.PATH_PREFIX)
public class HostResource implements HostApi {
    private static final Logger log = Logger.getLogger(HostResource.class.getName());

    private final Orchestrator orchestrator;

    @Inject
    public HostResource(@Component Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public GetHostResponse getHost(final String hostNameString) {
        final HostName hostName = new HostName(hostNameString);
        try {
            HostStatus status = orchestrator.getNodeStatus(hostName);
            return new GetHostResponse(hostName.s(), status.name());
        } catch (HostNameNotFoundException e) {
            throw new NotFoundException(e);
        }
    }

    @Override
    public UpdateHostResponse suspend(final String hostNameString) {
        final HostName hostName = new HostName(hostNameString);
        try {
            orchestrator.suspend(hostName);
        } catch (HostNameNotFoundException e) {
            throw new NotFoundException(e);
        } catch (HostStateChangeDeniedException e) {
            log.log(LogLevel.DEBUG, "Suspend for " + hostName + " failed.", e);
            throw webExceptionWithDenialReason(hostName, e);
        }
        return new UpdateHostResponse(hostName.s(), null);
    }

    @Override
    public UpdateHostResponse resume(final String hostNameString) {
        final HostName hostName = new HostName(hostNameString);
        try {
            orchestrator.resume(hostName);
        } catch (HostNameNotFoundException e) {
            throw new NotFoundException(e);
        } catch (HostStateChangeDeniedException e) {
            log.log(LogLevel.DEBUG, "Resume for " + hostName + " failed.", e);
            throw webExceptionWithDenialReason(hostName, e);
        }
        return new UpdateHostResponse(hostName.s(), null);
    }

    private static WebApplicationException webExceptionWithDenialReason(HostName hostName, HostStateChangeDeniedException e) {
        final HostStateChangeDenialReason hostStateChangeDenialReason = new HostStateChangeDenialReason(
                e.getConstraintName(), e.getServiceType().s(), e.getMessage());
        final UpdateHostResponse response = new UpdateHostResponse(hostName.s(), hostStateChangeDenialReason);
        return new WebApplicationException(
                hostStateChangeDenialReason.toString(),
                e,
                Response.status(Response.Status.CONFLICT)
                        .entity(response)
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());

    }
}

