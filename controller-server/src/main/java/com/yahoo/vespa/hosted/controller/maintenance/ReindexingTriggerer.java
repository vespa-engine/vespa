// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationReindexing;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodically triggers reindexing for all hosted Vespa applications.
 *
 * Since reindexing is meant to be a background effort, exactly when things are triggered is not critical,
 * and a hash of id of each deployment is used to spread triggering out across the reindexing period.
 * Only deployments within a window of opportunity of two maintainer periods are considered in each run.
 * Reindexing is triggered for a deployment if it was last triggered more than half a period ago, and
 * if no reindexing is currently ongoing. This means an application may skip reindexing during a period
 * if it happens to reindex, e.g., a particular document type in its window of opportunity. This is fine.
 *
 * @author jonmv
 */
public class ReindexingTriggerer extends ControllerMaintainer {

    static final Duration reindexingPeriod = Duration.ofDays(91); // 13 weeks — four times a year.

    private static final Logger log = Logger.getLogger(ReindexingTriggerer.class.getName());

    public ReindexingTriggerer(Controller controller, Duration duration) {
        super(controller, duration);
    }

    @Override
    protected boolean maintain() {
        try {
            Instant now = controller().clock().instant();
            for (Application application : controller().applications().asList())
                application.productionDeployments().forEach((name, deployments) -> {
                    ApplicationId id = application.id().instance(name);
                    for (Deployment deployment : deployments)
                        if (   inWindowOfOpportunity(now, interval(), id, deployment.zone())
                            && reindexingIsReady(controller().applications().applicationReindexing(id, deployment.zone()), now))
                            controller().applications().reindex(id, deployment.zone(), List.of(), List.of());
                });
            return true;
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to trigger reindexing: " + Exceptions.toMessageString(e));
            return false;
        }
    }

    static boolean inWindowOfOpportunity(Instant now, Duration interval, ApplicationId id, ZoneId zone) {
        long lastPeriodStartMillis = now.toEpochMilli() - (now.toEpochMilli() % reindexingPeriod.toMillis());
        Instant windowCenter = Instant.ofEpochMilli(lastPeriodStartMillis).plus(offset(id, zone));
        return windowCenter.minus(interval).isBefore(now) && now.isBefore(windowCenter.plus(interval));
    }

    static Duration offset(ApplicationId id, ZoneId zone) {
        double relativeOffset = ((id.serializedForm() + zone.value()).hashCode() & (-1 >>> 1)) / (double) (-1 >>> 1);
        return Duration.ofMillis((long) (reindexingPeriod.toMillis() * relativeOffset));
    }

    static boolean reindexingIsReady(ApplicationReindexing reindexing, Instant now) {
        if (reindexing.clusters().values().stream().flatMap(cluster -> cluster.ready().values().stream())
                      .anyMatch(status -> status.startedAt().isPresent() && status.endedAt().isEmpty()))
            return false;

        return reindexing.common().readyAt().orElse(Instant.EPOCH).isBefore(now.minus(reindexingPeriod.dividedBy(2)));
    }

}
