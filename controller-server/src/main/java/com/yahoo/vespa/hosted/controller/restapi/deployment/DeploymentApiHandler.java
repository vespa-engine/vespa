// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.JobList;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.restapi.Uri;
import com.yahoo.vespa.hosted.controller.restapi.application.EmptyJsonResponse;
import com.yahoo.vespa.hosted.controller.restapi.Path;
import com.yahoo.yolean.Exceptions;

import java.util.Optional;
import java.util.logging.Level;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError.outOfCapacity;
import static java.util.Comparator.comparing;

/**
 * This implements the deployment/v1 API which provides information about the status of Vespa platform and
 * application deployments.
 *
 * @author bratseth
 */
@SuppressWarnings("unused") // Injected
public class DeploymentApiHandler extends LoggingRequestHandler {

    private final Controller controller;

    public DeploymentApiHandler(LoggingRequestHandler.Context parentCtx, Controller controller) {
        super(parentCtx);
        this.controller = controller;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return handleGET(request);
                case OPTIONS: return handleOPTIONS();
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        }
        catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handleGET(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/deployment/v1/")) return root(request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handleOPTIONS() {
        // We implement this to avoid redirect loops on OPTIONS requests from browsers, but do not really bother
        // spelling out the methods supported at each path, which we should
        EmptyJsonResponse response = new EmptyJsonResponse();
        response.headers().put("Allow", "GET,OPTIONS");
        return response;
    }

    private HttpResponse root(HttpRequest request) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor platformArray = root.setArray("versions");
        for (VespaVersion version : controller.versionStatus().versions()) {
            Cursor versionObject = platformArray.addObject();
            versionObject.setString("version", version.versionNumber().toString());
            versionObject.setString("confidence", version.confidence().name());
            versionObject.setString("commit", version.releaseCommit());
            versionObject.setLong("date", version.committedAt().toEpochMilli());
            versionObject.setBool("controllerVersion", version.isSelfVersion());
            versionObject.setBool("systemVersion", version.isCurrentSystemVersion());

            Cursor configServerArray = versionObject.setArray("configServers");
            for (String configServerHostnames : version.configServerHostnames()) {
                Cursor configServerObject = configServerArray.addObject();
                configServerObject.setString("hostname", configServerHostnames);
            }

            Cursor failingArray = versionObject.setArray("failingApplications");
            for (ApplicationId id : version.statistics().failing()) {
                controller.applications().get(id).ifPresent(application -> {
                    firstFailingOn(version.versionNumber(), application).ifPresent(firstFailing -> {
                        Cursor applicationObject = failingArray.addObject();
                        toSlime(applicationObject, application, request);
                        applicationObject.setString("failing", firstFailing.type().jobName());
                    });
                });
            }

            Cursor productionArray = versionObject.setArray("productionApplications");
            for (ApplicationId id : version.statistics().production()) {
                controller.applications().get(id).ifPresent(application -> {
                    int successes = productionSuccessesFor(version.versionNumber(), application);
                    if (successes == 0) return; // Just upgraded to a newer version.
                    Cursor applicationObject = productionArray.addObject();
                    toSlime(applicationObject, application, request);
                    applicationObject.setLong("productionJobs", productionJobsFor(application));
                    applicationObject.setLong("productionSuccesses", productionSuccessesFor(version.versionNumber(), application));
                });
            }

            Cursor runningArray = versionObject.setArray("deployingApplications");
            for (ApplicationId id : version.statistics().deploying()) {
                controller.applications().get(id).ifPresent(application -> {
                    lastDeployingTo(version.versionNumber(), application).ifPresent(lastDeploying -> {
                        Cursor applicationObject = runningArray.addObject();
                        toSlime(applicationObject, application, request);
                        applicationObject.setString("running", lastDeploying.type().jobName());
                    });
                });
            }
        }
        return new SlimeJsonResponse(slime);
    }

    private void toSlime(Cursor object, Application application, HttpRequest request) {
        object.setString("tenant", application.id().tenant().value());
        object.setString("application", application.id().application().value());
        object.setString("instance", application.id().instance().value());
        object.setString("url", new Uri(request.getUri()).withPath("/application/v4/tenant/" +
                                                                   application.id().tenant().value() +
                                                                   "/application/" +
                                                                   application.id().application().value()).toString());
        object.setString("upgradePolicy", toString(application.deploymentSpec().upgradePolicy()));
    }

    private static String toString(DeploymentSpec.UpgradePolicy upgradePolicy) {
        if (upgradePolicy == DeploymentSpec.UpgradePolicy.defaultPolicy) {
            return "default";
        }
        return upgradePolicy.name();
    }

    // ----------------------------- Utilities to pick out the relevant JobStatus -- filter chains should mirror the ones in VersionStatus

    /** The first upgrade job to fail on this version, for this application */
    private Optional<JobStatus> firstFailingOn(Version version, Application application) {
        return JobList.from(application)
                .failing()
                .not().failingApplicationChange()
                .not().failingBecause(outOfCapacity)
                .lastCompleted().on(version)
                .asList().stream()
                .min(comparing(job -> job.lastCompleted().get().at()));
    }

    /** The number of production jobs for this application */
    private int productionJobsFor(Application application) {
        return JobList.from(application)
                .production()
                .size();
    }

    /** The number of production jobs with last success on the given version, for this application */
    private int productionSuccessesFor(Version version, Application application) {
        return JobList.from(application)
                .production()
                .lastSuccess().on(version)
                .size();
    }

    /** The last triggered upgrade to this version, for this application */
    private Optional<JobStatus> lastDeployingTo(Version version, Application application) {
        return JobList.from(application)
                .upgrading()
                .asList().stream()
                .max(comparing(job -> job.lastTriggered().get().at()));
    }

}
