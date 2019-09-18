// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.routing.GlobalRoutingService;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;
import com.yahoo.vespa.hosted.controller.rotation.RotationState;
import com.yahoo.vespa.hosted.controller.rotation.RotationStatus;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Periodically updates the status of assigned global rotations.
 *
 * @author mpolden
 */
public class RotationStatusUpdater extends Maintainer {

    private static final int applicationsToUpdateInParallel = 10;

    private final GlobalRoutingService service;
    private final ApplicationController applications;

    public RotationStatusUpdater(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
        this.service = controller.serviceRegistry().globalRoutingService();
        this.applications = controller.applications();
    }

    @Override
    protected void maintain() {
        var failures = new AtomicInteger(0);
        var lastException = new AtomicReference<Exception>(null);
        var applicationList = ApplicationList.from(applications.asList()).hasRotation();

        // Run parallel stream inside a custom ForkJoinPool so that we can control the number of threads used
        var pool = new ForkJoinPool(applicationsToUpdateInParallel);

        pool.submit(() -> {
            applicationList.asList().parallelStream().forEach(application -> {
                try {
                    applications.lockIfPresent(application.id(), (app) -> applications.store(app.with(getStatus(app.get()))));
                } catch (Exception e) {
                    failures.incrementAndGet();
                    lastException.set(e);
                }
            });
        });
        pool.shutdown();
        try {
            pool.awaitTermination(30, TimeUnit.SECONDS);
            if (lastException.get() != null) {
                log.log(LogLevel.WARNING, String.format("Failed to get global routing status of %d/%d applications. Retrying in %s. Last error: ",
                                                        failures.get(),
                                                        applicationList.size(),
                                                        maintenanceInterval()),
                        lastException.get());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private RotationStatus getStatus(Instance instance) {
        var statusMap = new LinkedHashMap<RotationId, Map<ZoneId, RotationState>>();
        for (var assignedRotation : instance.rotations()) {
            var rotation = applications.rotationRepository().getRotation(assignedRotation.rotationId());
            if (rotation.isEmpty()) continue;
            var rotationStatus = service.getHealthStatus(rotation.get().name()).entrySet().stream()
                                        .collect(Collectors.toMap(Map.Entry::getKey, (kv) -> from(kv.getValue())));
            statusMap.put(assignedRotation.rotationId(), rotationStatus);
        }
        return RotationStatus.from(statusMap);
    }

    private static RotationState from(com.yahoo.vespa.hosted.controller.api.integration.routing.RotationStatus status) {
        switch (status) {
            case IN: return RotationState.in;
            case OUT: return RotationState.out;
            case UNKNOWN: return RotationState.unknown;
            default: throw new IllegalArgumentException("Unknown API value for rotation status: " + status);
        }
    }

}
