package com.yahoo.vespa.hosted.controller.api.integration.billing;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @author ogronnesby
 */
public class StatusHistory {
    SortedMap<ZonedDateTime, BillStatus> history;

    public StatusHistory(SortedMap<ZonedDateTime, BillStatus> history) {
        this.history = history;
    }

    public static StatusHistory open(Clock clock) {
        var now = clock.instant().atZone(ZoneOffset.UTC);
        return new StatusHistory(
                new TreeMap<>(Map.of(now, BillStatus.OPEN))
        );
    }

    public BillStatus current() {
        return history.get(history.lastKey());
    }

    public SortedMap<ZonedDateTime, BillStatus> getHistory() {
        return history;
    }

}
