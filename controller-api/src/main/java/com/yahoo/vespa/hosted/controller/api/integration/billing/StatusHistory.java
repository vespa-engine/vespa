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
        // Validate the given history
        var iter = history.values().iterator();
        BillStatus next  = iter.hasNext() ? iter.next() : null;
        while (iter.hasNext()) {
            var current = next;
            next = iter.next();
            if (! validateStatus(current, next)) {
                throw new IllegalArgumentException("Invalid transition from " + current + " to " + next);
            }
        }

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

    public void checkValidTransition(BillStatus newStatus) {
        if (! validateStatus(current(), newStatus)) {
            throw new IllegalArgumentException("Invalid transition from " + current() + " to " + newStatus);
        }
    }

    private static boolean validateStatus(BillStatus current, BillStatus newStatus) {
        return switch(current) {
            case OPEN -> true;
            case FROZEN -> newStatus != BillStatus.OPEN; // This could be subject to change.
            case CLOSED -> newStatus == BillStatus.CLOSED;
            case VOID -> newStatus == BillStatus.VOID;
        };
    }

}
