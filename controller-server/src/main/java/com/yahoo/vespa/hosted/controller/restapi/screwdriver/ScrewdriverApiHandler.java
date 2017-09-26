// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.screwdriver;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService.BuildJob;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobReport;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.restapi.StringResponse;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This implements a callback API from Screwdriver which lets deployment jobs notify the controller
 * on completion.
 * 
 * @author bratseth
 */
public class ScrewdriverApiHandler extends LoggingRequestHandler {

    private final static Logger log = Logger.getLogger(ScrewdriverApiHandler.class.getName());

    private final Controller controller;
    // TODO: Remember to distinguish between PR jobs and component ones, by adding reports to the right jobs?

    public ScrewdriverApiHandler(Executor executor, AccessLog accessLog, Controller controller) {
        super(executor, accessLog);
        this.controller = controller;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            Method method = request.getMethod();
            String path = request.getUri().getPath();
            switch (method) {
                case GET: switch (path) {
                    case "/screwdriver/v1/release/vespa": return vespaVersion();
                    case "/screwdriver/v1/jobsToRun": return buildJobResponse(controller.applications().deploymentTrigger().buildSystem().jobs());
                    default: return ErrorResponse.notFoundError(String.format( "No '%s' handler at '%s'", method, path));
                }
                case POST: switch (path) {
                    case "/screwdriver/v1/jobreport": return handleJobReportPost(request);
                    default: return ErrorResponse.notFoundError(String.format( "No '%s' handler at '%s'", method, path));
                }
                case DELETE: switch (path) {
                    case "/screwdriver/v1/jobsToRun": return buildJobResponse(controller.applications().deploymentTrigger().buildSystem().takeJobsToRun());
                    default: return ErrorResponse.notFoundError(String.format( "No '%s' handler at '%s'", method, path));
                }
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        } catch (IllegalArgumentException|IllegalStateException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }
    
    private HttpResponse vespaVersion() {
        VespaVersion version = controller.versionStatus().version(controller.systemVersion());
        if (version == null) 
            return ErrorResponse.notFoundError("Information about the current system version is not available at this time");

        Slime slime = new Slime();
        Cursor cursor = slime.setObject();
        cursor.setString("version", version.versionNumber().toString());
        cursor.setString("sha", version.releaseCommit());
        cursor.setLong("date", version.releasedAt().toEpochMilli());
        return new SlimeJsonResponse(slime);
        
    }

    private HttpResponse buildJobResponse(List<BuildJob> buildJobs) {
        Slime slime = new Slime();
        Cursor buildJobArray = slime.setArray();
        for (BuildJob buildJob : buildJobs) {
            Cursor buildJobObject = buildJobArray.addObject();
            buildJobObject.setLong("projectId", buildJob.projectId());
            buildJobObject.setString("jobName", buildJob.jobName());
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse handleJobReportPost(HttpRequest request) {
        controller.applications().notifyJobCompletion(toJobReport(toSlime(request.getData()).get()));
        return new StringResponse("ok");
    }

    private Slime toSlime(InputStream jsonStream) {
        try {
            byte[] jsonBytes = IOUtils.readBytes(jsonStream, 1000 * 1000);
            return SlimeUtils.jsonToSlime(jsonBytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JobReport toJobReport(Inspector report) {
        Optional<JobError> jobError = Optional.empty();
        if (report.field("jobError").valid()) {
            jobError = Optional.of(JobError.valueOf(report.field("jobError").asString()));
        }
        return new JobReport(
                ApplicationId.from(
                        report.field("tenant").asString(),
                        report.field("application").asString(),
                        report.field("instance").asString()),
                JobType.fromId(report.field("jobName").asString()),
                report.field("projectId").asLong(),
                report.field("buildNumber").asLong(),
                jobError
        );
    }

}
