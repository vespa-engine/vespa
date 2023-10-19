package com.yahoo.vespa.hosted.controller.api.integration.billing;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @author gjoranv
 */
public class StatusHistory {
    SortedMap<ZonedDateTime, String> history;

    public StatusHistory(SortedMap<ZonedDateTime, String> history) {
        this.history = history;
    }

    public static StatusHistory open(Clock clock) {
        var now = clock.instant().atZone(ZoneOffset.UTC);
        return new StatusHistory(
                new TreeMap<>(Map.of(now, "OPEN"))
        );
    }

    public String current() {
        return history.get(history.lastKey());
    }

    public SortedMap<ZonedDateTime, String> getHistory() {
        return history;
    }

}
