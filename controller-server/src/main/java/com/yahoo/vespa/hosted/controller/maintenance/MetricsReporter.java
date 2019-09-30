// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.vespa.hosted.controller.application.JobList;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author mortent
 * @author mpolden
 */
public class MetricsReporter extends Maintainer {

    public static final String DEPLOYMENT_FAIL_METRIC = "deployment.failurePercentage";
    public static final String DEPLOYMENT_AVERAGE_DURATION = "deployment.averageDuration";
    public static final String DEPLOYMENT_FAILING_UPGRADES = "deployment.failingUpgrades";
    public static final String DEPLOYMENT_BUILD_AGE_SECONDS = "deployment.buildAgeSeconds";
    public static final String DEPLOYMENT_WARNINGS = "deployment.warnings";
    public static final String REMAINING_ROTATIONS = "remaining_rotations";
    public static final String NAME_SERVICE_REQUESTS_QUEUED = "dns.queuedRequests";

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
    }

    private void reportRemainingRotations() {
        try (RotationLock lock = controller().applications().rotationRepository().lock()) {
            int availableRotations = controller().applications().rotationRepository().availableRotations(lock).size();
            metric.set(REMAINING_ROTATIONS, availableRotations, metric.createContext(Collections.emptyMap()));
        }
    }

    private void reportDeploymentMetrics() {
        List<Instance> instances = ApplicationList.from(controller().applications().asList())
                                                  .withProductionDeployment().asList().stream()
                                                  .flatMap(application -> application.instances().values().stream())
                                                  .collect(Collectors.toUnmodifiableList());

        metric.set(DEPLOYMENT_FAIL_METRIC, deploymentFailRatio(instances) * 100, metric.createContext(Collections.emptyMap()));

        averageDeploymentDurations(instances, clock.instant()).forEach((application, duration) -> {
            metric.set(DEPLOYMENT_AVERAGE_DURATION, duration.getSeconds(), metric.createContext(dimensions(application)));
        });

        deploymentsFailingUpgrade(instances).forEach((application, failingJobs) -> {
            metric.set(DEPLOYMENT_FAILING_UPGRADES, failingJobs, metric.createContext(dimensions(application)));
        });

        deploymentWarnings(instances).forEach((application, warnings) -> {
            metric.set(DEPLOYMENT_WARNINGS, warnings, metric.createContext(dimensions(application)));
        });

        for (Instance instance : instances)
            instance.deploymentJobs().statusOf(JobType.component)
                    .flatMap(JobStatus::lastSuccess)
                    .flatMap(run -> run.application().buildTime())
                    .ifPresent(buildTime -> metric.set(DEPLOYMENT_BUILD_AGE_SECONDS,
                                                          controller().clock().instant().getEpochSecond() - buildTime.getEpochSecond(),
                                                          metric.createContext(dimensions(instance.id()))));
    }

    private void reportQueuedNameServiceRequests() {
        metric.set(NAME_SERVICE_REQUESTS_QUEUED, controller().curator().readNameServiceQueue().requests().size(),
                   metric.createContext(Map.of()));
    }
    
    private static double deploymentFailRatio(List<Instance> instances) {
        return instances.stream()
                        .mapToInt(instance -> instance.deploymentJobs().hasFailures() ? 1 : 0)
                        .average().orElse(0);
    }

    private static Map<ApplicationId, Duration> averageDeploymentDurations(List<Instance> instances, Instant now) {
        return instances.stream()
                        .collect(Collectors.toMap(Instance::id,
                                                  instance -> averageDeploymentDuration(instance, now)));
    }

    private static Map<ApplicationId, Integer> deploymentsFailingUpgrade(List<Instance> instances) {
        return instances.stream()
                        .collect(Collectors.toMap(Instance::id, MetricsReporter::deploymentsFailingUpgrade));
    }

    private static int deploymentsFailingUpgrade(Instance instance) {
        return JobList.from(instance).upgrading().failing().size();
    }

    private static Duration averageDeploymentDuration(Instance instance, Instant now) {
        List<Duration> jobDurations = instance.deploymentJobs().jobStatus().values().stream()
                                              .filter(status -> status.lastTriggered().isPresent())
                                              .map(status -> {
                                                     Instant triggeredAt = status.lastTriggered().get().at();
                                                     Instant runningUntil = status.lastCompleted()
                                                                                  .map(JobStatus.JobRun::at)
                                                                                  .filter(at -> at.isAfter(triggeredAt))
                                                                                  .orElse(now);
                                                     return Duration.between(triggeredAt, runningUntil);
                                                 })
                                              .collect(Collectors.toList());
        return jobDurations.stream()
                           .reduce(Duration::plus)
                           .map(totalDuration -> totalDuration.dividedBy(jobDurations.size()))
                           .orElse(Duration.ZERO);
    }

    private static Map<ApplicationId, Integer> deploymentWarnings(List<Instance> instances) {
        return instances.stream()
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
        return ImmutableMap.of(
                "tenant", application.tenant().value(),
                "app",application.application().value() + "." + application.instance().value()
        );
    }

}


