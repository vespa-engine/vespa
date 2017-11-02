// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.restapi.Uri;
import com.yahoo.vespa.hosted.controller.restapi.application.EmptyJsonResponse;
import com.yahoo.vespa.hosted.controller.restapi.Path;
import com.yahoo.yolean.Exceptions;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;

import static com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError.outOfCapacity;
import static java.util.Comparator.comparing;

/**
 * This implements the deployment/v1 API which provides information about the status of Vespa platform and
 * application deployments.
 *
 * @author bratseth
 */
public class DeploymentApiHandler extends LoggingRequestHandler {

    private final Controller controller;

    public DeploymentApiHandler(Executor executor, AccessLog accessLog, Controller controller) {
        super(executor, accessLog);
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
            versionObject.setLong("date", version.releasedAt().toEpochMilli());
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
                        toSlime(failingArray.addObject(), application, firstFailing.type(), request);
                    });
                });
            }

            Cursor productionArray = versionObject.setArray("productionApplications");
            for (ApplicationId id : version.statistics().production()) {
                controller.applications().get(id).ifPresent(application -> {
                    lastProductionOn(version.versionNumber(), application).ifPresent(lastProduction -> {
                        toSlime(productionArray.addObject(), application, lastProduction.type(), request);
                    });
                });
            }

            Cursor deployingArray = versionObject.setArray("deployingApplications");
            for (ApplicationId id : version.statistics().deploying()) {
                controller.applications().get(id).ifPresent(application -> {
                    lastDeployingTo(version.versionNumber(), application).ifPresent(lastDeploying -> {
                        toSlime(deployingArray.addObject(), application, lastDeploying.type(), request);
                    });
                });
            }
        }
        return new SlimeJsonResponse(slime);
    }

    private void toSlime(Cursor object, Application application, JobType jobType, HttpRequest request) {
        object.setString("tenant", application.id().tenant().value());
        object.setString("application", application.id().application().value());
        object.setString("instance", application.id().instance().value());
        object.setString("url", new Uri(request.getUri()).withPath("/application/v4/tenant/" +
                                                                   application.id().tenant().value() +
                                                                   "/application/" +
                                                                   application.id().application().value()).toString());
        object.setString("upgradePolicy", toString(application.deploymentSpec().upgradePolicy()));
        object.setString("jobType", jobType.id());
    }

    private static String toString(DeploymentSpec.UpgradePolicy upgradePolicy) {
        if (upgradePolicy == DeploymentSpec.UpgradePolicy.defaultPolicy) {
            return "default";
        }
        return upgradePolicy.name();
    }

    // ----------------------------- Utilities to pick out the relevant JobStatus -- filter chains should mirror the ones in VersionStatus

    /** The first upgrade job to fail for this version x application */
    private Optional<JobStatus> firstFailingOn(Version version, Application application) {
        return application.deploymentJobs().jobStatus().values().stream()
                .filter(jobStatus -> jobStatus.lastCompleted().isPresent())
                .filter(jobStatus -> jobStatus.lastCompleted().get().upgrade())
                .filter(jobStatus -> jobStatus.jobError().isPresent())
                .filter(jobStatus -> jobStatus.jobError().get() != outOfCapacity)
                .filter(jobStatus -> jobStatus.lastCompleted().get().version().equals(version))
                .min(comparing(jobStatus -> jobStatus.lastCompleted().get().at()));
    }

    /** The last production job to succeed for this version x application */
    private Optional<JobStatus> lastProductionOn(Version version, Application application) {
        return application.deploymentJobs().jobStatus().values().stream()
                .filter(jobStatus -> jobStatus.lastSuccess().isPresent())
                .filter(jobStatus -> jobStatus.type().isProduction())
                .filter(jobStatus -> jobStatus.lastSuccess().get().version().equals(version))
                .max(comparing(jobStatus -> jobStatus.lastSuccess().get().at()));
    }

    /** The last deployment triggered for this version x application */
    private Optional<JobStatus> lastDeployingTo(Version version, Application application) {
        return application.deploymentJobs().jobStatus().values().stream()
                .filter(jobStatus -> jobStatus.isRunning(controller.applications().deploymentTrigger().jobTimeoutLimit()))
                .filter(jobStatus -> jobStatus.lastTriggered().get().version().equals(version))
                .max(comparing(jobStatus -> jobStatus.lastTriggered().get().at()));
    }

}
