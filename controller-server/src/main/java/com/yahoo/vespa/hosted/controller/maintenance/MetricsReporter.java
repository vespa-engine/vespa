// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.concurrent.maintenance.JobControl;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatusList;
import com.yahoo.vespa.hosted.controller.deployment.JobList;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;
import com.yahoo.vespa.hosted.controller.versions.NodeVersion;
import com.yahoo.vespa.hosted.controller.versions.NodeVersions;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This calculates and reports system-wide metrics based on data from a {@link Controller}.
 *
 * @author mortent
 * @author mpolden
 */
public class MetricsReporter extends ControllerMaintainer {

    public static final String DEPLOYMENT_FAIL_METRIC = "deployment.failurePercentage";
    public static final String DEPLOYMENT_AVERAGE_DURATION = "deployment.averageDuration";
    public static final String DEPLOYMENT_FAILING_UPGRADES = "deployment.failingUpgrades";
    public static final String DEPLOYMENT_BUILD_AGE_SECONDS = "deployment.buildAgeSeconds";
    public static final String DEPLOYMENT_WARNINGS = "deployment.warnings";
    public static final String OS_CHANGE_DURATION = "deployment.osChangeDuration";
    public static final String PLATFORM_CHANGE_DURATION = "deployment.platformChangeDuration";
    public static final String REMAINING_ROTATIONS = "remaining_rotations";
    public static final String NAME_SERVICE_REQUESTS_QUEUED = "dns.queuedRequests";

    private final Metric metric;
    private final Clock clock;

    public MetricsReporter(Controller controller, Metric metric) {
        super(controller, Duration.ofMinutes(1)); // use fixed rate for metrics
        this.metric = metric;
        this.clock = controller.clock();
    }

    @Override
    public void maintain() {
        reportDeploymentMetrics();
        reportRemainingRotations();
        reportQueuedNameServiceRequests();
        reportChangeDurations();
    }

    private void reportRemainingRotations() {
        try (RotationLock lock = controller().routing().rotations().lock()) {
            int availableRotations = controller().routing().rotations().availableRotations(lock).size();
            metric.set(REMAINING_ROTATIONS, availableRotations, metric.createContext(Map.of()));
        }
    }

    private void reportDeploymentMetrics() {
        ApplicationList applications = ApplicationList.from(controller().applications().readable())
                                                      .withProductionDeployment();
        DeploymentStatusList deployments = controller().jobController().deploymentStatuses(applications);

        metric.set(DEPLOYMENT_FAIL_METRIC, deploymentFailRatio(deployments) * 100, metric.createContext(Map.of()));

        averageDeploymentDurations(deployments, clock.instant()).forEach((instance, duration) -> {
            metric.set(DEPLOYMENT_AVERAGE_DURATION, duration.getSeconds(), metric.createContext(dimensions(instance)));
        });

        deploymentsFailingUpgrade(deployments).forEach((instance, failingJobs) -> {
            metric.set(DEPLOYMENT_FAILING_UPGRADES, failingJobs, metric.createContext(dimensions(instance)));
        });

        deploymentWarnings(deployments).forEach((application, warnings) -> {
            metric.set(DEPLOYMENT_WARNINGS, warnings, metric.createContext(dimensions(application)));
        });

        for (Application application : applications.asList())
            application.latestVersion()
                       .flatMap(ApplicationVersion::buildTime)
                       .ifPresent(buildTime -> metric.set(DEPLOYMENT_BUILD_AGE_SECONDS,
                                                          controller().clock().instant().getEpochSecond() - buildTime.getEpochSecond(),
                                                          metric.createContext(dimensions(application.id().defaultInstance()))));
    }

    private void reportQueuedNameServiceRequests() {
        metric.set(NAME_SERVICE_REQUESTS_QUEUED, controller().curator().readNameServiceQueue().requests().size(),
                   metric.createContext(Map.of()));
    }

    private void reportChangeDurations() {
        Map<NodeVersion, Duration> platformChangeDurations = platformChangeDurations();
        Map<NodeVersion, Duration> osChangeDurations = osChangeDurations();
        platformChangeDurations.forEach((nodeVersion, duration) -> {
            metric.set(PLATFORM_CHANGE_DURATION, duration.toSeconds(), metric.createContext(dimensions(nodeVersion)));
        });
        osChangeDurations.forEach((nodeVersion, duration) -> {
            metric.set(OS_CHANGE_DURATION, duration.toSeconds(), metric.createContext(dimensions(nodeVersion)));
        });
    }

    private Map<NodeVersion, Duration> platformChangeDurations() {
        return changeDurations(controller().versionStatus().versions(), VespaVersion::nodeVersions);
    }

    private Map<NodeVersion, Duration> osChangeDurations() {
        return changeDurations(controller().osVersionStatus().versions().values(), Function.identity());
    }

    private <V> Map<NodeVersion, Duration> changeDurations(Collection<V> versions, Function<V, NodeVersions> versionsGetter) {
        var now = clock.instant();
        var durations = new HashMap<NodeVersion, Duration>();
        for (var version : versions) {
            for (var nodeVersion : versionsGetter.apply(version).asMap().values()) {
                durations.put(nodeVersion, nodeVersion.changeDuration(now));
            }
        }
        return durations;
    }

    private static double deploymentFailRatio(DeploymentStatusList statuses) {
        return statuses.asList().stream()
                       .mapToInt(status -> status.hasFailures() ? 1 : 0)
                       .average().orElse(0);
    }

    private static Map<ApplicationId, Duration> averageDeploymentDurations(DeploymentStatusList statuses, Instant now) {
        return statuses.asList().stream()
                       .flatMap(status -> status.instanceJobs().entrySet().stream())
                       .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey(),
                                                             entry -> averageDeploymentDuration(entry.getValue(), now)));
    }

    private static Map<ApplicationId, Integer> deploymentsFailingUpgrade(DeploymentStatusList statuses) {
        return statuses.asList().stream()
                       .flatMap(status -> status.instanceJobs().entrySet().stream())
                       .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey(),
                                                             entry -> deploymentsFailingUpgrade(entry.getValue())));
    }

    private static int deploymentsFailingUpgrade(JobList jobs) {
        return jobs.failing().not().failingApplicationChange().size();
    }

    private static Duration averageDeploymentDuration(JobList jobs, Instant now) {
        List<Duration> jobDurations = jobs.lastTriggered()
                                          .mapToList(run -> Duration.between(run.start(), run.end().orElse(now)));
        return jobDurations.stream()
                           .reduce(Duration::plus)
                           .map(totalDuration -> totalDuration.dividedBy(jobDurations.size()))
                           .orElse(Duration.ZERO);
    }

    private static Map<ApplicationId, Integer> deploymentWarnings(DeploymentStatusList statuses) {
        return statuses.asList().stream()
                       .flatMap(status -> status.application().instances().values().stream())
                        .collect(Collectors.toMap(Instance::id, a -> maxWarningCountOf(a.deployments().values())));
    }

    private static int maxWarningCountOf(Collection<Deployment> deployments) {
        return deployments.stream()
                          .map(Deployment::metrics)
                          .map(DeploymentMetrics::warnings)
                          .map(Map::values)
                          .flatMap(Collection::stream)
                          .max(Integer::compareTo)
                          .orElse(0);
    }

    private static Map<String, String> dimensions(ApplicationId application) {
        return Map.of("tenant", application.tenant().value(),
                      "app",application.application().value() + "." + application.instance().value());
    }

    private static Map<String, String> dimensions(NodeVersion nodeVersion) {
        return Map.of("host", nodeVersion.hostname().value(),
                      "zone", nodeVersion.zone().value());
    }

}


