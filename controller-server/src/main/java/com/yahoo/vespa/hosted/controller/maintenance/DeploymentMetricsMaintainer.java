// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Retrieve deployment metrics such as QPS and document count from the metric service and
 * update applications with this info.
 *
 * @author smorgrav
 * @author mpolden
 */
public class DeploymentMetricsMaintainer extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(DeploymentMetricsMaintainer.class.getName());

    private static final int applicationsToUpdateInParallel = 10;

    private final ApplicationController applications;

    public DeploymentMetricsMaintainer(Controller controller, Duration duration) {
        super(controller, duration, DeploymentMetricsMaintainer.class.getSimpleName(), SystemName.all());
        this.applications = controller.applications();
    }

    @Override
    protected boolean maintain() {
        AtomicInteger failures = new AtomicInteger(0);
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicReference<Exception> lastException = new AtomicReference<>(null);

        // Run parallel stream inside a custom ForkJoinPool so that we can control the number of threads used
        ForkJoinPool pool = new ForkJoinPool(applicationsToUpdateInParallel);
        pool.submit(() ->
            applications.readable().parallelStream().forEach(application -> {
                for (Instance instance : application.instances().values())
                    for (Deployment deployment : instance.deployments().values()) {
                        attempts.incrementAndGet();
                        try {
                            if (deployment.version().getMajor() < 7) continue;
                            DeploymentId deploymentId = new DeploymentId(instance.id(), deployment.zone());
                            List<ClusterMetrics> clusterMetrics = controller().serviceRegistry().configServer().getDeploymentMetrics(deploymentId);
                            Instant now = controller().clock().instant();

                            applications.lockApplicationIfPresent(application.id(), locked -> {
                                Deployment existingDeployment = locked.get().require(instance.name()).deployments().get(deployment.zone());
                                if (existingDeployment == null) return; // Deployment removed since we started collecting metrics
                                DeploymentMetrics newMetrics = updateDeploymentMetrics(existingDeployment.metrics(), clusterMetrics).at(now);
                                applications.store(locked.with(instance.name(),
                                                               lockedInstance -> lockedInstance.with(existingDeployment.zone(), newMetrics)
                                                                                               .recordActivityAt(now, existingDeployment.zone())));

                                controller().notificationsDb().setDeploymentFeedingBlockedNotifications(deploymentId, clusterMetrics);
                            });
                        } catch (Exception e) {
                            failures.incrementAndGet();
                            lastException.set(e);
                        }
                    }
            })
        );
        pool.shutdown();
        try {
            pool.awaitTermination(30, TimeUnit.MINUTES);
            if (lastException.get() != null) {
                log.log(Level.WARNING,
                        String.format("Failed to gather metrics for %d/%d applications. Retrying in %s. Last error: %s",
                                      failures.get(),
                                      attempts.get(),
                                      interval(),
                                      Exceptions.toMessageString(lastException.get())));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return lastException.get() == null;
    }

    static DeploymentMetrics updateDeploymentMetrics(DeploymentMetrics current, List<ClusterMetrics> metrics) {
        return current
                .withQueriesPerSecond(metrics.stream().flatMap(m -> m.queriesPerSecond().stream()).mapToDouble(Double::doubleValue).sum())
                .withWritesPerSecond(metrics.stream().flatMap(m -> m.feedPerSecond().stream()).mapToDouble(Double::doubleValue).sum())
                .withDocumentCount(metrics.stream().flatMap(m -> m.documentCount().stream()).mapToLong(Double::longValue).sum())
                .withQueryLatencyMillis(weightedAverageLatency(metrics, ClusterMetrics::queriesPerSecond, ClusterMetrics::queryLatency))
                .withWriteLatencyMillis(weightedAverageLatency(metrics, ClusterMetrics::feedPerSecond, ClusterMetrics::feedLatency));
    }

    private static double weightedAverageLatency(List<ClusterMetrics> metrics,
                                                 Function<ClusterMetrics, Optional<Double>> rateExtractor,
                                                 Function<ClusterMetrics, Optional<Double>> latencyExtractor) {
        double rateSum = metrics.stream().flatMap(m -> rateExtractor.apply(m).stream()).mapToDouble(Double::longValue).sum();
        if (rateSum == 0) return 0.0;

        double weightedLatency = metrics.stream()
                .flatMap(m -> latencyExtractor.apply(m).flatMap(l -> rateExtractor.apply(m).map(r -> l * r)).stream())
                .mapToDouble(Double::doubleValue)
                .sum();

        return weightedLatency / rateSum;
    }
}
