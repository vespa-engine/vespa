// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.screwdriver;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService.BuildJob;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.Path;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.yolean.Exceptions;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This implements a callback API from Screwdriver which lets deployment jobs notify the controller
 * on completion.
 * 
 * @author bratseth
 * @author mpolden
 */
@SuppressWarnings("unused") // Handler
public class ScrewdriverApiHandler extends LoggingRequestHandler {

    private final static Logger log = Logger.getLogger(ScrewdriverApiHandler.class.getName());

    private final Controller controller;

    public ScrewdriverApiHandler(LoggingRequestHandler.Context parentCtx, Controller controller) {
        super(parentCtx);
        this.controller = controller;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        Method method = request.getMethod();
        try {
            switch (method) {
                case GET: return get(request);
                case POST: return post(request);
                default: return ErrorResponse.methodNotAllowed("Method '" + method + "' is unsupported");
            }
        } catch (IllegalArgumentException|IllegalStateException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/screwdriver/v1/release/vespa")) {
            return vespaVersion();
        }
        if (path.matches("/screwdriver/v1/jobsToRun")) {
            return buildJobs(controller.applications().deploymentTrigger().deploymentQueue().jobs());
        }
        return notFound(request);
    }

    private HttpResponse post(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/screwdriver/v1/trigger/tenant/{tenant}/application/{application}")) {
            return trigger(request, path.get("tenant"), path.get("application"));
        }
        return notFound(request);
    }

    private HttpResponse trigger(HttpRequest request, String tenantName, String applicationName) {
        JobType jobType = Optional.of(asString(request.getData()))
                .filter(s -> !s.isEmpty())
                .map(JobType::fromJobName)
                .orElse(JobType.component);

        ApplicationId applicationId = ApplicationId.from(tenantName, applicationName, "default");
        controller.applications().lockOrThrow(applicationId, application -> {
            // Since this is a manual operation we likely want it to trigger as soon as possible so we add it at to the
            // front of the queue
            application = controller.applications().deploymentTrigger().triggerAllowParallel(
                    jobType, application, true, true,
                    "Triggered from screwdriver/v1"
            );
            controller.applications().store(application);
        });

        Slime slime = new Slime();
        Cursor cursor = slime.setObject();
        cursor.setString("message", "Triggered " + jobType.jobName() + " for " + applicationId);
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse vespaVersion() {
        VespaVersion version = controller.versionStatus().version(controller.systemVersion());
        if (version == null) 
            return ErrorResponse.notFoundError("Information about the current system version is not available at this time");

        Slime slime = new Slime();
        Cursor cursor = slime.setObject();
        cursor.setString("version", version.versionNumber().toString());
        cursor.setString("sha", version.releaseCommit());
        cursor.setLong("date", version.committedAt().toEpochMilli());
        return new SlimeJsonResponse(slime);
        
    }

    private HttpResponse buildJobs(List<BuildJob> buildJobs) {
        Slime slime = new Slime();
        Cursor buildJobArray = slime.setArray();
        for (BuildJob buildJob : buildJobs) {
            Cursor buildJobObject = buildJobArray.addObject();
            buildJobObject.setLong("projectId", buildJob.projectId());
            buildJobObject.setString("jobName", buildJob.jobName());
        }
        return new SlimeJsonResponse(slime);
    }

    private static String asString(InputStream in) {
        Scanner scanner = new Scanner(in).useDelimiter("\\A");
        if (scanner.hasNext()) {
            return scanner.next();
        }
        return "";
    }

    private static HttpResponse notFound(HttpRequest request) {
        return ErrorResponse.notFoundError(String.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

}
