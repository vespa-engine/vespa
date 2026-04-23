// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * A window of time during which changes (revisions, versions, maintenance) are blocked.
 *
 * @author olaa
 */
public record BlockWindow(boolean revision, boolean version, boolean maintenance, List<DayOfWeek> days, List<Integer> hours, ZoneId zone) {

    public BlockWindow(boolean revision, boolean version, boolean maintenance, List<DayOfWeek> days, List<Integer> hours, ZoneId zone) {
        this.revision    = revision;
        this.version     = version;
        this.maintenance = maintenance;
        this.days        = List.copyOf(days);
        this.hours       = List.copyOf(hours);
        this.zone        = zone;
    }

    /** Returns whether this window is currently blocking platform (Vespa version) upgrades */
    public boolean blocksPlatformAt(Instant instant) {
        return version && isWithin(instant);
    }

    /** Returns whether this window is currently blocking revision (application package) deployments */
    public boolean blocksRevisionAt(Instant instant) {
        return revision && isWithin(instant);
    }

    /** Returns whether this window is currently blocking maintenance operations */
    public boolean blocksMaintenanceAt(Instant instant) {
        return maintenance && isWithin(instant);
    }

    private boolean isWithin(Instant instant) {
        var localTime = instant.atZone(zone);
        return days.contains(localTime.getDayOfWeek()) && hours.contains(localTime.getHour());
    }

}
