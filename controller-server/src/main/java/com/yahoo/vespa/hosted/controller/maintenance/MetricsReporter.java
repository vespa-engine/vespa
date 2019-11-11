// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

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
import com.yahoo.vespa.hosted.controller.versions.NodeVersions;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
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
public class MetricsReporter extends Maintainer {

    public static final String DEPLOYMENT_FAIL_METRIC = "deployment.failurePercentage";
    public static final String DEPLOYMENT_AVERAGE_DURATION = "deployment.averageDuration";
    public static final String DEPLOYMENT_FAILING_UPGRADES = "deployment.failingUpgrades";
    public static final String DEPLOYMENT_BUILD_AGE_SECONDS = "deployment.buildAgeSeconds";
    public static final String DEPLOYMENT_WARNINGS = "deployment.warnings";
    public static final String NODES_FAILING_SYSTEM_UPGRADE = "deployment.nodesFailingSystemUpgrade";
    public static final String NODES_FAILING_OS_UPGRADE = "deployment.nodesFailingOsUpgrade";
    public static final String REMAINING_ROTATIONS = "remaining_rotations";
    public static final String NAME_SERVICE_REQUESTS_QUEUED = "dns.queuedRequests";

    private static final Duration NODE_UPGRADE_TIMEOUT = Duration.ofHours(1);
    private static final Duration OS_UPGRADE_TIME_ALLOWANCE_PER_NODE = Duration.ofMinutes(30);

    private final Metric metric;
    private final Clock clock;

    public MetricsReporter(Controller controller, Metric metric, JobControl jobControl) {
        super(controller, Duration.ofMinutes(1), jobControl); // use fixed rate for metrics
        this.metric = metric;
        this.clock = controller.clock();
    }

    @Override
    public void maintain() {
        reportDeploymentMetrics();
        reportRemainingRotations();
        reportQueuedNameServiceRequests();
        reportNodesFailingUpgrade();
    }

    private void reportRemainingRotations() {
        try (RotationLock lock = controller().applications().rotationRepository().lock()) {
            int availableRotations = controller().applications().rotationRepository().availableRotations(lock).size();
            metric.set(REMAINING_ROTATIONS, availableRotations, metric.createContext(Map.of()));
        }
    }

    private void reportDeploymentMetrics() {
        ApplicationList applications = ApplicationList.from(controller().applications().asList())
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

    private void reportNodesFailingUpgrade() {
        metric.set(NODES_FAILING_SYSTEM_UPGRADE, nodesFailingSystemUpgrade(), metric.createContext(Map.of()));
        metric.set(NODES_FAILING_OS_UPGRADE, nodesFailingOsUpgrade(), metric.createContext(Map.of()));
    }

    private int nodesFailingSystemUpgrade() {
        if (!controller().versionStatus().isUpgrading()) return 0;
        return nodesFailingUpgrade(controller().versionStatus().versions(), (vespaVersion) -> {
            if (vespaVersion.confidence() == VespaVersion.Confidence.broken) return NodeVersions.EMPTY;
            return vespaVersion.nodeVersions();
        }, NODE_UPGRADE_TIMEOUT);
    }

    private int nodesFailingOsUpgrade() {
        var allNodeVersions = controller().osVersionStatus().versions().values();
        var totalTimeout = 0L;
        for (var nodeVersions : allNodeVersions) {
            for (var nodeVersion : nodeVersions.asMap().values()) {
                if (!nodeVersion.changing()) continue;
                totalTimeout += OS_UPGRADE_TIME_ALLOWANCE_PER_NODE.toMillis();
            }
        }
        return nodesFailingUpgrade(allNodeVersions, Function.identity(), Duration.ofMillis(totalTimeout));
    }

    private <V> int nodesFailingUpgrade(Collection<V> collection, Function<V, NodeVersions> nodeVersionsFunction, Duration timeout) {
        var nodesFailingUpgrade = 0;
        var acceptableInstant = clock.instant().minus(timeout);
        for (var object : collection) {
            for (var nodeVersion : nodeVersionsFunction.apply(object).asMap().values()) {
                if (!nodeVersion.changing()) continue;
                if (nodeVersion.changedAt().isBefore(acceptableInstant)) nodesFailingUpgrade++;
            }
        }
        return nodesFailingUpgrade;
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

}


