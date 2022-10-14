// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.restapi.UriBuilder;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Versions;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.vespa.hosted.controller.restapi.application.EmptyResponse;
import com.yahoo.vespa.hosted.controller.versions.DeploymentStatistics;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.yolean.Exceptions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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
public class DeploymentApiHandler extends ThreadedHttpRequestHandler {

    private final Controller controller;

    public DeploymentApiHandler(ThreadedHttpRequestHandler.Context parentCtx, Controller controller) {
        super(parentCtx);
        this.controller = controller;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            return switch (request.getMethod()) {
                case GET -> handleGET(request);
                case OPTIONS -> handleOPTIONS();
                default -> ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            };
        }
        catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
        catch (RuntimeException e) {
            return ErrorResponses.logThrowing(request, log, e);
        }
    }

    private HttpResponse handleGET(HttpRequest request) {
        Path path = new Path(request.getUri());
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
        var versionStatus = controller.readVersionStatus();
        ApplicationList applications = ApplicationList.from(controller.applications().asList()).withJobs();
        var deploymentStatuses = controller.jobController().deploymentStatuses(applications, versionStatus);
        Map<Version, DeploymentStatistics> deploymentStatistics = DeploymentStatistics.compute(versionStatus.versions().stream().map(VespaVersion::versionNumber).collect(toList()),
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
            for (var nodeVersion : version.nodeVersions()) {
                Cursor configServerObject = configServerArray.addObject();
                configServerObject.setString("hostname", nodeVersion.hostname().value());
            }

            DeploymentStatistics statistics = deploymentStatistics.get(version.versionNumber());
            Cursor failingArray = versionObject.setArray("failingApplications");
            for (Run run : statistics.failingUpgrades()) {
                Cursor applicationObject = failingArray.addObject();
                toSlime(applicationObject, run.id().application(), request);
                applicationObject.setString("failing", run.id().type().jobName());
                applicationObject.setString("status", nameOf(run.status()));
            }

            var statusByInstance = deploymentStatuses.asList().stream()
                                                     .flatMap(status -> status.instanceJobs().keySet().stream()
                                                                              .map(instance -> Map.entry(instance, status)))
                                                     .collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
            var jobsByInstance = statusByInstance.entrySet().stream()
                                                 .collect(toUnmodifiableMap(Map.Entry::getKey,
                                                                            entry -> entry.getValue().instanceJobs().get(entry.getKey())));
            Cursor productionArray = versionObject.setArray("productionApplications");
            statistics.productionSuccesses().stream()
                      .collect(groupingBy(run -> run.id().application(), TreeMap::new, toList()))
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
                      var status = statusByInstance.get(instance);
                      var jobsToRun = status.jobsToRun();
                      Cursor instanceObject = instancesArray.addObject();
                      instanceObject.setString("tenant", instance.tenant().value());
                      instanceObject.setString("application", instance.application().value());
                      instanceObject.setString("instance", instance.instance().value());
                      instanceObject.setBool("upgrading", status.application().require(instance.instance()).change().platform().equals(Optional.of(statistics.version())));
                      DeploymentStatus.StepStatus stepStatus = status.instanceSteps().get(instance.instance());
                      if (stepStatus != null) { // Instance may not have any steps, i.e. an empty deployment spec has been submitted
                          stepStatus.blockedUntil(Change.of(statistics.version()))
                                    .ifPresent(until -> instanceObject.setLong("blockedUntil", until.toEpochMilli()));
                      }
                      instanceObject.setString("upgradePolicy", toString(status.application().deploymentSpec().instance(instance.instance())
                                                                               .map(DeploymentInstanceSpec::upgradePolicy)
                                                                               .orElse(DeploymentSpec.UpgradePolicy.defaultPolicy)));
                      status.application().revisions().last().flatMap(ApplicationVersion::compileVersion)
                            .ifPresent(compiled -> instanceObject.setString("compileVersion", compiled.toFullString()));
                      Cursor jobsArray = instanceObject.setArray("jobs");
                      status.jobSteps().forEach((job, jobStatus) -> {
                          if ( ! job.application().equals(instance)) return;
                          Cursor jobObject = jobsArray.addObject();
                          jobObject.setString("name", job.type().jobName());
                          jobStatus.pausedUntil().ifPresent(until -> jobObject.setLong("pausedUntil", until.toEpochMilli()));
                          jobStatus.coolingDownUntil(status.application().require(instance.instance()).change(), Optional.empty())
                                   .ifPresent(until -> jobObject.setLong("coolingDownUntil", until.toEpochMilli()));
                          if (jobsToRun.containsKey(job)) {
                              List<Versions> versionsOnThisPlatform = jobsToRun.get(job).stream()
                                                                               .map(DeploymentStatus.Job::versions)
                                                                               .filter(versions -> versions.targetPlatform().equals(statistics.version()))
                                                                               .toList();
                              if ( ! versionsOnThisPlatform.isEmpty())
                                  jobObject.setString("pending", versionsOnThisPlatform.stream()
                                                                                       .allMatch(versions -> versions.sourcePlatform()
                                                                                                                     .map(statistics.version()::equals)
                                                                                                                     .orElse(true))
                                                                 ? "application" : "platform");
                          }
                      });
                      Cursor allRunsObject = instanceObject.setObject("allRuns");
                      Cursor upgradeRunsObject = instanceObject.setObject("upgradeRuns");
                      runs.forEach((type, rs) -> {
                          Cursor runObject = allRunsObject.setObject(type.jobName());
                          Cursor upgradeObject = upgradeRunsObject.setObject(type.jobName());
                          for (RunInfo run : rs) {
                              toSlime(runObject, run.run);
                              if (run.upgrade)
                                  toSlime(upgradeObject, run.run);
                          }
                      });
                  });
        }
        JobType.allIn(controller.zoneRegistry()).stream()
               .filter(job -> ! job.environment().isManuallyDeployed())
               .map(JobType::jobName).forEach(root.setArray("jobs")::addString);
        return new SlimeJsonResponse(slime);
    }

    private void toSlime(Cursor jobObject, Run run) {
        String key = run.hasFailed() ? "failing" : run.hasEnded() ? "success" : "running";
        Cursor runObject = jobObject.setObject(key);
        runObject.setLong("number", run.id().number());
        runObject.setLong("start", run.start().toEpochMilli());
        run.end().ifPresent(end -> runObject.setLong("end", end.toEpochMilli()));
        runObject.setString("status", nameOf(run.status()));
    }

    private void toSlime(Cursor object, ApplicationId id, HttpRequest request) {
        object.setString("tenant", id.tenant().value());
        object.setString("application", id.application().value());
        object.setString("instance", id.instance().value());
        object.setString("url", new UriBuilder(request.getUri()).withPath("/application/v4/tenant/" +
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

    public static String nameOf(RunStatus status) {
        return switch (status) {
            case reset, running                       -> "running";
            case aborted                              -> "aborted";
            case error                                -> "error";
            case testFailure                          -> "testFailure";
            case noTests                              -> "noTests";
            case endpointCertificateTimeout           -> "endpointCertificateTimeout";
            case nodeAllocationFailure                -> "nodeAllocationFailure";
            case installationFailed                   -> "installationFailed";
            case invalidApplication, deploymentFailed -> "deploymentFailed";
            case success                              -> "success";
        };
    }

    private static class RunInfo {
        final Run run;
        final boolean upgrade;

        RunInfo(Run run, boolean upgrade) {
            this.run = run;
            this.upgrade = upgrade;
        }

        @Override
        public String toString() {
            return run.id().toString();
        }

    }


}
