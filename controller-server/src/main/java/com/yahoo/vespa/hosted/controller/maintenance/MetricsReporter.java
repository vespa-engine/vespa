// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.chef.AttributeMapping;
import com.yahoo.vespa.hosted.controller.api.integration.chef.Chef;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.PartialNode;
import com.yahoo.vespa.hosted.controller.api.integration.chef.rest.PartialNodeResult;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
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
            if (application.isPresent()) {
                dimensions.put("app",String.format("%s.%s", application.get(), node.getValue("instance").orElse("default")));
            }
            Metric.Context context = metric.createContext(dimensions);
            metric.set(convergeMetric, secondsSinceConverge, context);
        }
    }

    private void reportDeploymentMetrics() {
        metric.set(deploymentFailMetric, deploymentFailRatio() * 100, metric.createContext(Collections.emptyMap()));
        for (Map.Entry<ApplicationId, Duration> entry : averageDeploymentDurations().entrySet()) {
            metric.set(deploymentAverageDuration, entry.getValue().getSeconds(),
                       metric.createContext(Collections.singletonMap("application", entry.getKey().toString())));
        }
    }
    
    private double deploymentFailRatio() {
        List<Application> applications = ApplicationList.from(controller().applications().asList())
                                                        .notPullRequest()
                                                        .hasProductionDeployment()               
                                                        .asList();
        if (applications.isEmpty()) return 0;

        return (double) applications.stream().filter(a -> a.deploymentJobs().hasFailures()).count() /
               (double) applications.size();
    }

    private Map<ApplicationId, Duration> averageDeploymentDurations() {
        Instant now = clock.instant();
        return ApplicationList.from(controller().applications().asList())
                              .notPullRequest()
                              .hasProductionDeployment()
                              .asList()
                              .stream()
                              .collect(Collectors.toMap(Application::id,
                                                        application -> averageDeploymentDuration(application, now)));
    }

    private Duration averageDeploymentDuration(Application application, Instant now) {
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
    
    private void keepNodesWithSystem(PartialNodeResult nodeResult, SystemName system) {
        nodeResult.rows.removeIf(node -> !system.name().equals(node.getValue("system").orElse("main")));
    }

}


