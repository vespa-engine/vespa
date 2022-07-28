// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationReindexing;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationReindexing.Cluster;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationReindexing.Status;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static com.yahoo.vespa.hosted.controller.maintenance.ReindexingTriggerer.inWindowOfOpportunity;
import static com.yahoo.vespa.hosted.controller.maintenance.ReindexingTriggerer.reindexingIsReady;
import static com.yahoo.vespa.hosted.controller.maintenance.ReindexingTriggerer.reindexingPeriod;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReindexingTriggererTest {

    @Test
    void testWindowOfOpportunity() {
        Duration interval = Duration.ofHours(1);
        Instant now = Instant.now();
        Instant doom = now.plus(ReindexingTriggerer.reindexingPeriod);
        int triggered = 0;
        while (now.isBefore(doom)) {
            if (inWindowOfOpportunity(now, ApplicationId.defaultId(), ZoneId.defaultId())) {
                triggered++;
                ZonedDateTime time = ZonedDateTime.ofInstant(now, java.time.ZoneId.of("Europe/Oslo"));
                assertTrue(List.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY).contains(time.getDayOfWeek()));
                assertTrue(List.of(8, 9, 10, 11).contains(time.getHour()));
            }
            now = now.plus(interval);
        }
        // Summer/winter time :'(
        assertTrue(3 <= triggered && triggered <= 5, "Should be in window of opportunity three to five times each period");
    }

    @Test
    void testReindexingIsReady() {
        Instant then = Instant.now();
        ApplicationReindexing reindexing = new ApplicationReindexing(true,
                Map.of("c", new Cluster(Map.of(), Map.of("d", new Status(then)))));

        Instant now = then;
        assertFalse(reindexingIsReady(reindexing, now),
                "Should not be ready less than one half-period after last triggering");

        now = now.plus(reindexingPeriod.dividedBy(2));
        assertFalse(reindexingIsReady(reindexing, now),
                "Should not be ready one half-period after last triggering");

        now = now.plusMillis(1);
        assertTrue(reindexingIsReady(reindexing, now),
                "Should be ready more than one half-period after last triggering");

        reindexing = new ApplicationReindexing(true,
                Map.of("cluster",
                        new Cluster(Map.of(),
                                Map.of("type",
                                        new Status(then, then, null, null, null, null, 1.0)))));
        assertFalse(reindexingIsReady(reindexing, now),
                "Should not be ready when reindexing is already running");

        reindexing = new ApplicationReindexing(true,
                Map.of("cluster",
                        new Cluster(Map.of("type", 123L),
                                Map.of("type",
                                        new Status(then, then, now, null, null, null, 1.0)))));
        assertTrue(reindexingIsReady(reindexing, now),
                "Should be ready when reindexing is no longer running");
    }

}
