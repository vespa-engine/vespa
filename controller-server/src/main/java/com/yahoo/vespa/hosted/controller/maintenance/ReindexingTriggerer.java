// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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
    static final double speed = 0.2; // Careful reindexing, as this is supposed to be a background operation.

    private static final Logger log = Logger.getLogger(ReindexingTriggerer.class.getName());

    public ReindexingTriggerer(Controller controller, Duration duration) {
        super(controller, duration);
    }

    @Override
    protected double maintain() {
        try {
            Instant now = controller().clock().instant();
            for (Application application : controller().applications().asList())
                application.productionDeployments().forEach((name, deployments) -> {
                    ApplicationId id = application.id().instance(name);
                    for (Deployment deployment : deployments)
                        if (   inWindowOfOpportunity(now, id, deployment.zone())
                            && reindexingIsReady(controller().applications().applicationReindexing(id, deployment.zone()), now))
                            controller().applications().reindex(id, deployment.zone(), List.of(), List.of(), true, speed);
                });
            return 1.0;
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to trigger reindexing: " + Exceptions.toMessageString(e));
            return 0.0;
        }
    }

    static boolean inWindowOfOpportunity(Instant now, ApplicationId id, ZoneId zone) {
        long dayOfPeriodToTrigger = Math.floorMod((id.serializedForm() + zone.value()).hashCode(), 65); // 13 weeks a 5 week days.
        long weekOfPeriodToTrigger = dayOfPeriodToTrigger / 5;
        long dayOfWeekToTrigger = dayOfPeriodToTrigger % 5;
        long daysSinceFirstMondayAfterEpoch = Instant.EPOCH.plus(Duration.ofDays(4)).until(now, ChronoUnit.DAYS); // EPOCH was a Thursday.
        long weekOfPeriod = (daysSinceFirstMondayAfterEpoch / 7) % 13; // 7 days to a calendar week, 13 weeks to the period.
        long dayOfWeek = daysSinceFirstMondayAfterEpoch % 7;
        long hourOfTrondheimTime = ZonedDateTime.ofInstant(now, java.time.ZoneId.of("Europe/Oslo")).getHour();

        return    weekOfPeriod == weekOfPeriodToTrigger
               && dayOfWeek == dayOfWeekToTrigger
               && 8 <= hourOfTrondheimTime && hourOfTrondheimTime < 12;
    }

    static boolean reindexingIsReady(ApplicationReindexing reindexing, Instant now) {
        return reindexing.clusters().values().stream().flatMap(cluster -> cluster.ready().values().stream())
                         .allMatch(status ->    status.readyAt().map(now.minus(reindexingPeriod.dividedBy(2))::isAfter).orElse(true)
                                             && (status.startedAt().isEmpty() || status.endedAt().isPresent()));
    }

}
