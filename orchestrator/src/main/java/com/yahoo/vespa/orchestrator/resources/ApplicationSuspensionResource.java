// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.orchestrator.ApplicationIdNotFoundException;
import com.yahoo.vespa.orchestrator.ApplicationStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.orchestrator.restapi.ApplicationSuspensionApi;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author smorgrav
 */
@Path(ApplicationSuspensionApi.PATH_PREFIX)
public class ApplicationSuspensionResource implements ApplicationSuspensionApi {

    private static final Logger log = Logger.getLogger(ApplicationSuspensionResource.class.getName());

    private final OrchestratorImpl orchestrator;

    @Inject
    public ApplicationSuspensionResource(@Component OrchestratorImpl orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public Set<String> getApplications() {
        Set<ApplicationId> refs = orchestrator.getAllSuspendedApplications();
        return refs.stream().map(ApplicationId::serializedForm).collect(Collectors.toSet());
    }

    @Override
    public void getApplication(String applicationIdString) {
        ApplicationId appId = toApplicationId(applicationIdString);
        ApplicationInstanceStatus status;

        try {
             status = orchestrator.getApplicationInstanceStatus(appId);
        } catch (ApplicationIdNotFoundException e) {
            throw new NotFoundException("Application " + applicationIdString + " could not be found");
        }

        if (status.equals(ApplicationInstanceStatus.NO_REMARKS)) {
            throw new NotFoundException("Application " + applicationIdString + " is not suspended");
        }

        // Return void as we have nothing to return except 204 No
        // Content. Unfortunately, Jersey outputs a warning for this case:
        //
        //     The following warnings have been detected: HINT: A HTTP GET
        //     method, public void com.yahoo.vespa.orchestrator.resources.
        //     ApplicationSuspensionResource.getApplication(java.lang.String),
        //     returns a void type. It can be intentional and perfectly fine,
        //     but it is a little uncommon that GET method returns always "204
        //     No Content"
        //
        // We have whitelisted the warning for our systemtests.
        //
        // bakksjo has a pending jersey PR fix that avoids making the hint
        // become a warning:
        //     https://github.com/jersey/jersey/pull/212
        //
        // TODO: Remove whitelisting and this comment once jersey has been
        // fixed.
    }

    @Override
    public void suspend(String applicationIdString) {
        ApplicationId applicationId = toApplicationId(applicationIdString);
        try {
            orchestrator.suspend(applicationId);
        } catch (ApplicationIdNotFoundException e) {
            log.log(LogLevel.INFO, "ApplicationId " + applicationIdString + " not found.", e);
            throw new NotFoundException(e);
        } catch (ApplicationStateChangeDeniedException e) {
            log.log(LogLevel.INFO, "Suspend for " + applicationIdString + " failed.", e);
            throw new WebApplicationException(Response.Status.CONFLICT);
        } catch (RuntimeException e) {
            log.log(LogLevel.INFO, "Suspend for " + applicationIdString + " failed from unknown reasons", e);
            throw new InternalServerErrorException(e);
        }
    }

    @Override
    public void resume(String applicationIdString) {
        ApplicationId applicationId = toApplicationId(applicationIdString);
        try {
            orchestrator.resume(applicationId);
        } catch (ApplicationIdNotFoundException e) {
            log.log(LogLevel.INFO, "ApplicationId " + applicationIdString + " not found.", e);
            throw new NotFoundException(e);
        } catch (ApplicationStateChangeDeniedException e) {
            log.log(LogLevel.INFO, "Suspend for " + applicationIdString + " failed.", e);
            throw new WebApplicationException(Response.Status.CONFLICT);
        } catch (RuntimeException e) {
            log.log(LogLevel.INFO, "Suspend for " + applicationIdString + " failed from unknown reasons", e);
            throw new InternalServerErrorException(e);
        }
    }

    private ApplicationId toApplicationId(String applicationIdString) {
        try {
            return ApplicationId.fromSerializedForm(applicationIdString);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e);
        }
    }

}
