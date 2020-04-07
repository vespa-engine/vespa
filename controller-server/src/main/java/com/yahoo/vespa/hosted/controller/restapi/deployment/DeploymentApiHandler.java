// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

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
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.restapi.application.EmptyResponse;
import com.yahoo.vespa.hosted.controller.versions.DeploymentStatistics;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.yolean.Exceptions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableMap;

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
        var deploymentStatuses = controller.jobController().deploymentStatuses(ApplicationList.from(controller.applications().asList()), systemVersion);
        var deploymentStatistics = DeploymentStatistics.compute(versionStatus.versions().stream().map(VespaVersion::versionNumber).collect(toList()),
                                                                deploymentStatuses)
                                                       .stream().collect(toMap(DeploymentStatistics::version, identity()));
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

            DeploymentStatistics statistics = deploymentStatistics.get(version.versionNumber());
            Cursor failingArray = versionObject.setArray("failingApplications");
            for (Run run : statistics.failingUpgrades()) {
                Cursor applicationObject = failingArray.addObject();
                toSlime(applicationObject, run.id().application(), request);
                applicationObject.setString("failing", run.id().type().jobName());
                applicationObject.setString("status", run.status().name());
            }

            var jobsByInstance = deploymentStatuses.asList().stream()
                                                   .flatMap(status -> status.instanceJobs().entrySet().stream())
                                                   .collect(toUnmodifiableMap(jobs -> jobs.getKey(), jobs -> jobs.getValue()));
            Cursor productionArray = versionObject.setArray("productionApplications");
            statistics.productionSuccesses().stream()
                      .collect(groupingBy(run -> run.id().application()))
                      .forEach((id, runs) -> {
                          Cursor applicationObject = productionArray.addObject();
                          toSlime(applicationObject, id, request);
                          applicationObject.setLong("productionJobs", jobsByInstance.get(id).production().size());
                          applicationObject.setLong("productionSuccesses", runs.size());
                      });

            Cursor runningArray = versionObject.setArray("deployingApplications");
            for (Run run : statistics.runningUpgrade()) {
                Cursor applicationObject = runningArray.addObject();
                toSlime(applicationObject, run.id().application(), request);
                applicationObject.setString("running", run.id().type().jobName());
            }

            class RunInfo { //  ヽ༼ຈل͜ຈ༽━☆ﾟ.*･｡ﾟ
                final Run run;
                final boolean upgrade;
                RunInfo(Run run, boolean upgrade) { this.run = run; this.upgrade = upgrade; }
                @Override public String toString() { return run.id().toString(); }
            }
            Cursor instancesArray = versionObject.setArray("applications");
            Stream.of(statistics.failingUpgrades().stream().map(run -> new RunInfo(run, true)),
                      statistics.otherFailing().stream().map(run -> new RunInfo(run, false)),
                      statistics.runningUpgrade().stream().map(run -> new RunInfo(run, true)),
                      statistics.otherRunning().stream().map(run -> new RunInfo(run, false)),
                      statistics.productionSuccesses().stream().map(run -> new RunInfo(run, true)))
                  .flatMap(identity())
                  .collect(Collectors.groupingBy(run -> run.run.id().application(),
                                                 LinkedHashMap::new, // Put apps with failing and running jobs first.
                                                 groupingBy(run -> run.run.id().type(),
                                                            LinkedHashMap::new,
                                                            toList())))
                  .forEach((instance, runs) -> {
                      Cursor instanceObject = instancesArray.addObject();
                      instanceObject.setString("tenant", instance.tenant().value());
                      instanceObject.setString("application", instance.application().value());
                      instanceObject.setString("instance", instance.instance().value());
                      instanceObject.setLong("productionJobCount", jobsByInstance.get(instance).production().size());
                      instanceObject.setString("upgradePolicy", toString(deploymentStatuses.matching(status -> status.application().id().equals(TenantAndApplicationId.from(instance)))
                                                                                           .first().map(status -> status.application().deploymentSpec())
                                                                                           .flatMap(spec -> spec.instance(instance.instance()).map(DeploymentInstanceSpec::upgradePolicy))
                                                                                           .orElse(DeploymentSpec.UpgradePolicy.defaultPolicy)));
                      Cursor allJobsObject = instanceObject.setObject("allJobs");
                      Cursor upgradeJobsObject = instanceObject.setObject("upgradeJobs");
                      runs.forEach((type, rs) -> {
                          Cursor jobObject = allJobsObject.setObject(type.jobName());
                          Cursor upgradeObject = upgradeJobsObject.setObject(type.jobName());
                          for (RunInfo run : rs) {
                              toSlime(jobObject, run.run);
                              if (run.upgrade)
                                  toSlime(upgradeObject, run.run);
                          }
                      });
                  });
        }
        JobType.allIn(controller.system()).stream().map(JobType::jobName).forEach(root.setArray("jobs")::addString);
        return new SlimeJsonResponse(slime);
    }

    private void toSlime(Cursor jobObject, Run run) {
        String key = run.hasFailed() ? "failing" : run.hasEnded() ? "success" : "running";
        Cursor runObject = jobObject.setObject(key);
        runObject.setLong("number", run.id().number());
        runObject.setLong("start", run.start().toEpochMilli());
        run.end().ifPresent(end -> runObject.setLong("end", end.toEpochMilli()));
        runObject.setString("status", run.status().name());
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

}
