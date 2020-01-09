// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.restapi.Uri;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.JobList;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.restapi.application.EmptyResponse;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.yolean.Exceptions;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * This implements the deployment/v1 API which provides information about the status of Vespa platform and
 * application deployments.
 *
 * @author bratseth
 */
@SuppressWarnings("unused") // Injected
public class DeploymentApiHandler extends LoggingRequestHandler {

    private static final String OPTIONAL_PREFIX = "/api";

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
        Path path = new Path(request.getUri(), OPTIONAL_PREFIX);
        if (path.matches("/deployment/v1/")) return root(request);
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse handleOPTIONS() {
        // We implement this to avoid redirect loops on OPTIONS requests from browsers, but do not really bother
        // spelling out the methods supported at each path, which we should
        EmptyResponse response = new EmptyResponse();
        response.headers().put("Allow", "GET,OPTIONS");
        return response;
    }

    private HttpResponse root(HttpRequest request) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor platformArray = root.setArray("versions");
        var versionStatus = controller.versionStatus();
        var systemVersion = versionStatus.systemVersion().map(VespaVersion::versionNumber).orElse(Vtag.currentVersion);
        Map<ApplicationId, JobList> jobs = controller.jobController().deploymentStatuses(ApplicationList.from(controller.applications().asList()), systemVersion)
                                                     .asList().stream()
                                                     .flatMap(status -> status.instanceJobs().entrySet().stream())
                                                     .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        for (VespaVersion version : versionStatus.versions()) {
            Cursor versionObject = platformArray.addObject();
            versionObject.setString("version", version.versionNumber().toString());
            versionObject.setString("confidence", version.confidence().name());
            versionObject.setString("commit", version.releaseCommit());
            versionObject.setLong("date", version.committedAt().toEpochMilli());
            versionObject.setBool("controllerVersion", version.isControllerVersion());
            versionObject.setBool("systemVersion", version.isSystemVersion());

            Cursor configServerArray = versionObject.setArray("configServers");
            for (HostName hostname : version.nodeVersions().hostnames()) {
                Cursor configServerObject = configServerArray.addObject();
                configServerObject.setString("hostname", hostname.value());
            }

            Cursor failingArray = versionObject.setArray("failingApplications");
            for (ApplicationId id : version.statistics().failing()) {
                if (jobs.containsKey(id))
                    firstFailingOn(version.versionNumber(), jobs.get(id)).ifPresent(firstFailing -> {
                        Cursor applicationObject = failingArray.addObject();
                        toSlime(applicationObject, id, request);
                        applicationObject.setString("failing", firstFailing.id().type().jobName());
                    });
            }

            Cursor productionArray = versionObject.setArray("productionApplications");
            for (ApplicationId id : version.statistics().production()) {
                if (jobs.containsKey(id)) {
                    int successes = productionSuccessesFor(version.versionNumber(), jobs.get(id));
                    if (successes == 0) continue; // Just upgraded to a newer version.
                    Cursor applicationObject = productionArray.addObject();
                    toSlime(applicationObject, id, request);
                    applicationObject.setLong("productionJobs", jobs.get(id).production().size());
                    applicationObject.setLong("productionSuccesses", productionSuccessesFor(version.versionNumber(), jobs.get(id)));
                }
            }

            Cursor runningArray = versionObject.setArray("deployingApplications");
            for (ApplicationId id : version.statistics().deploying()) {
                if (jobs.containsKey(id))
                    lastDeployingTo(version.versionNumber(), jobs.get(id)).ifPresent(lastDeploying -> {
                        Cursor applicationObject = runningArray.addObject();
                        toSlime(applicationObject, id, request);
                        applicationObject.setString("running", lastDeploying.id().type().jobName());
                    });
            }
        }
        return new SlimeJsonResponse(slime);
    }

    private void toSlime(Cursor object, ApplicationId id, HttpRequest request) {
        object.setString("tenant", id.tenant().value());
        object.setString("application", id.application().value());
        object.setString("instance", id.instance().value());
        object.setString("url", new Uri(request.getUri()).withPath("/application/v4/tenant/" +
                                                                   id.tenant().value() +
                                                                   "/application/" +
                                                                   id.application().value()).toString());
        object.setString("upgradePolicy", toString(controller.applications().requireApplication(TenantAndApplicationId.from(id))
                                                             .deploymentSpec().instance(id.instance()).map(DeploymentInstanceSpec::upgradePolicy)
                                                             .orElse(DeploymentSpec.UpgradePolicy.defaultPolicy)));
    }

    private static String toString(DeploymentSpec.UpgradePolicy upgradePolicy) {
        if (upgradePolicy == DeploymentSpec.UpgradePolicy.defaultPolicy) {
            return "default";
        }
        return upgradePolicy.name();
    }

    // ----------------------------- Utilities to pick out the relevant JobStatus -- filter chains should mirror the ones in VersionStatus

    /** The first upgrade job to fail on this version, for this application */
    private Optional<Run> firstFailingOn(Version version, JobList jobs) {
        return jobs.failing()
                   .not().failingApplicationChange()
                   .not().withStatus(RunStatus.outOfCapacity)
                   .lastCompleted().on(version)
                   .lastCompleted().asList().stream()
                   .min(Comparator.<Run, Instant>comparing(run -> run.start())
                                .thenComparing(run -> run.id().type()));
    }

    /** The number of production jobs with last success on the given version, for this application */
    private int productionSuccessesFor(Version version, JobList jobs) {
        return jobs.production()
                   .lastSuccess().on(version)
                   .size();
    }

    /** The last triggered upgrade to this version, for this application */
    private Optional<Run> lastDeployingTo(Version version, JobList jobs) {
        return jobs.upgrading()
                   .lastTriggered().on(version)
                   .lastTriggered().asList().stream()
                   .max(Comparator.<Run, Instant>comparing(run -> run.start())
                                .thenComparing(run -> run.id().type()));
    }

}
