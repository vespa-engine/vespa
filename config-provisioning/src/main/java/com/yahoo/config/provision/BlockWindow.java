// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.List;

/**
 * A window of time during which changes (revisions, versions) are blocked.
 *
 * @author olaa
 */
public record BlockWindow(boolean revision, boolean version, List<DayOfWeek> days, List<Integer> hours, ZoneId zone) {

    public BlockWindow(boolean revision, boolean version, List<DayOfWeek> days, List<Integer> hours, ZoneId zone) {
        this.revision = revision;
        this.version  = version;
        this.days     = List.copyOf(days);
        this.hours    = List.copyOf(hours);
        this.zone     = zone;
    }

}
