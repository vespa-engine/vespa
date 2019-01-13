// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.chef.AttributeMapping;
import com.yahoo.vespa.hosted.controller.api.integration.chef.Chef;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.PartialNode;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.PartialNodeResult;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.JobList;
import com.yahoo.vespa.hosted.controller.application.JobStatus;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author mortent
 * @author mpolden
 */
public class MetricsReporter extends Maintainer {

    public static final String convergeMetric = "seconds.since.last.chef.convergence";
    public static final String deploymentFailMetric = "deployment.failurePercentage";
    public static final String deploymentAverageDuration = "deployment.averageDuration";
    public static final String deploymentFailingUpgrades = "deployment.failingUpgrades";
    public static final String deploymentBuildAgeSeconds = "deployment.buildAgeSeconds";
    public static final String remainingRotations = "remaining_rotations";

    private final Metric metric;
    private final Chef chefClient;
    private final Clock clock;
    private final SystemName system;

    public MetricsReporter(Controller controller, Metric metric, Chef chefClient, JobControl jobControl,
                           SystemName system) {
        this(controller, metric, chefClient, Clock.systemUTC(), jobControl, system);
    }

    public MetricsReporter(Controller controller, Metric metric, Chef chefClient, Clock clock,
                           JobControl jobControl, SystemName system) {
        super(controller, Duration.ofMinutes(1), jobControl); // use fixed rate for metrics
        this.metric = metric;
        this.chefClient = chefClient;
        this.clock = clock;
        this.system = system;
    }

    @Override
    public void maintain() {
        reportChefMetrics();
        reportDeploymentMetrics();
        reportRemainingRotations();
    }

    private void reportRemainingRotations() {
        try (RotationLock lock = controller().applications().rotationRepository().lock()) {
            int availableRotations = controller().applications().rotationRepository().availableRotations(lock).size();
            metric.set(remainingRotations, availableRotations, metric.createContext(Collections.emptyMap()));
        }
    }

    private void reportChefMetrics() {
        String query = "chef_environment:hosted*";
        if (system == SystemName.cd) {
            query += " AND hosted_system:" + system;
        }
        PartialNodeResult nodeResult = chefClient.partialSearchNodes(query,
                Arrays.asList(
                        AttributeMapping.simpleMapping("fqdn"),
                        AttributeMapping.simpleMapping("ohai_time"),
                        AttributeMapping.deepMapping("tenant", Arrays.asList("hosted", "owner", "tenant")),
                        AttributeMapping.deepMapping("application", Arrays.asList("hosted", "owner", "application")),
                        AttributeMapping.deepMapping("instance", Arrays.asList("hosted", "owner", "instance")),
                        AttributeMapping.deepMapping("environment", Arrays.asList("hosted", "environment")),
                        AttributeMapping.deepMapping("region", Arrays.asList("hosted", "region")),
                        AttributeMapping.deepMapping("system", Arrays.asList("hosted", "system"))
                ));

        // The above search will return a correct list if the system is CD. However for main, it will
        // return all nodes, since system==nil for main
        keepNodesWithSystem(nodeResult, system);
        
        Instant instant = clock.instant();
        for (PartialNode node : nodeResult.rows) {
            String hostname = node.getFqdn();
            long secondsSinceConverge = Duration.between(Instant.ofEpochSecond(node.getOhaiTime().longValue()), instant).getSeconds();
            Map<String, String> dimensions = new HashMap<>();
            dimensions.put("host", hostname);
            dimensions.put("system", node.getValue("system").orElse("main"));
            Optional<String> environment = node.getValue("environment");
            Optional<String> region = node.getValue("region");

            if(environment.isPresent() && region.isPresent()) {
                dimensions.put("zone", String.format("%s.%s", environment.get(), region.get()));
            }

            node.getValue("tenant").ifPresent(tenant -> dimensions.put("tenantName", tenant));
            Optional<String> application = node.getValue("application");
            application.ifPresent(app -> dimensions.put("app", String.format("%s.%s", app, node.getValue("instance").orElse("default"))));
            Metric.Context context = metric.createContext(dimensions);
            metric.set(convergeMetric, secondsSinceConverge, context);
        }
    }

    private void reportDeploymentMetrics() {
        ApplicationList applications = ApplicationList.from(controller().applications().asList())
                                                      .hasProductionDeployment();

        metric.set(deploymentFailMetric, deploymentFailRatio(applications) * 100, metric.createContext(Collections.emptyMap()));

        averageDeploymentDurations(applications, clock.instant()).forEach((application, duration) -> {
            metric.set(deploymentAverageDuration, duration.getSeconds(), metric.createContext(dimensions(application)));
        });

        deploymentsFailingUpgrade(applications).forEach((application, failingJobs) -> {
            metric.set(deploymentFailingUpgrades, failingJobs, metric.createContext(dimensions(application)));
        });

        for (Application application : applications.asList())
            application.deploymentJobs().statusOf(JobType.component)
                       .flatMap(JobStatus::lastSuccess)
                       .flatMap(run -> run.application().buildTime())
                       .ifPresent(buildTime -> metric.set(deploymentBuildAgeSeconds,
                                                          controller().clock().instant().getEpochSecond() - buildTime.getEpochSecond(),
                                                          metric.createContext(dimensions(application.id()))));
    }
    
    private static double deploymentFailRatio(ApplicationList applicationList) {
        List<Application> applications = applicationList.asList();
        if (applications.isEmpty()) return 0;

        return (double) applications.stream().filter(a -> a.deploymentJobs().hasFailures()).count() /
               (double) applications.size();
    }

    private static Map<ApplicationId, Duration> averageDeploymentDurations(ApplicationList applications, Instant now) {
        return applications.asList().stream()
                           .collect(Collectors.toMap(Application::id,
                                                     application -> averageDeploymentDuration(application, now)));
    }

    private static Map<ApplicationId, Integer> deploymentsFailingUpgrade(ApplicationList applications) {
        return applications.asList()
                           .stream()
                           .collect(Collectors.toMap(Application::id, MetricsReporter::deploymentsFailingUpgrade));
    }

    private static int deploymentsFailingUpgrade(Application application) {
        return JobList.from(application).upgrading().failing().size();
    }

    private static Duration averageDeploymentDuration(Application application, Instant now) {
        List<Duration> jobDurations = application.deploymentJobs().jobStatus().values().stream()
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
    
    private static void keepNodesWithSystem(PartialNodeResult nodeResult, SystemName system) {
        nodeResult.rows.removeIf(node -> !system.name().equals(node.getValue("system").orElse("main")));
    }

    private static Map<String, String> dimensions(ApplicationId application) {
        return ImmutableMap.of(
                "tenant", application.tenant().value(),
                "app",application.application().value() + "." + application.instance().value()
        );
    }

}


