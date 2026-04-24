// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * A window of time during which changes (revisions, versions, maintenance) are blocked.
 *
 * @author olaa
 */
public record BlockWindow(boolean revision, boolean version, boolean maintenance,
                          List<DayOfWeek> days, List<Integer> hours, ZoneId zone,
                          Optional<LocalDate> fromDate, Optional<LocalDate> toDate) {

    public BlockWindow(boolean revision, boolean version, List<DayOfWeek> days, List<Integer> hours, ZoneId zone) {
        this(revision, version, false, days, hours, zone, Optional.empty(), Optional.empty());
    }

    public BlockWindow(boolean revision, boolean version, boolean maintenance, List<DayOfWeek> days, List<Integer> hours, ZoneId zone) {
        this(revision, version, maintenance, days, hours, zone, Optional.empty(), Optional.empty());
    }

    public BlockWindow(boolean revision, boolean version, boolean maintenance,
                       List<DayOfWeek> days, List<Integer> hours, ZoneId zone,
                       Optional<LocalDate> fromDate, Optional<LocalDate> toDate) {
        this.revision    = revision;
        this.version     = version;
        this.maintenance = maintenance;
        this.days        = List.copyOf(days);
        this.hours       = List.copyOf(hours);
        this.zone        = zone;
        this.fromDate    = fromDate;
        this.toDate      = toDate;
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
        if (!days.contains(localTime.getDayOfWeek())) return false;
        if (!hours.contains(localTime.getHour())) return false;
        LocalDate date = localTime.toLocalDate();
        if (fromDate.isPresent() && date.isBefore(fromDate.get())) return false;
        if (toDate.isPresent() && date.isAfter(toDate.get())) return false;
        return true;
    }

}
