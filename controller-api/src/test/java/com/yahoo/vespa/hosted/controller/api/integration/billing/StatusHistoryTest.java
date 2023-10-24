package com.yahoo.vespa.hosted.controller.api.integration.billing;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author gjoranv
 */
public class StatusHistoryTest {

    private final Clock clock = Clock.systemUTC();

    @Test
    void open_can_change_to_any_status() {
        var history = StatusHistory.open(clock);
        history.checkValidTransition(BillStatus.FROZEN);
        history.checkValidTransition(BillStatus.CLOSED);
        history.checkValidTransition(BillStatus.VOID);
    }

    @Test
    void frozen_cannot_change_to_open() {
        var history = new StatusHistory(historyWith(BillStatus.FROZEN));

        history.checkValidTransition(BillStatus.CLOSED);
        history.checkValidTransition(BillStatus.VOID);

        assertThrows(IllegalArgumentException.class, () -> history.checkValidTransition(BillStatus.OPEN));
    }

    @Test
    void closed_cannot_change() {
        var history = new StatusHistory(historyWith(BillStatus.CLOSED));

        assertThrows(IllegalArgumentException.class, () -> history.checkValidTransition(BillStatus.OPEN));
        assertThrows(IllegalArgumentException.class, () -> history.checkValidTransition(BillStatus.FROZEN));
        assertThrows(IllegalArgumentException.class, () -> history.checkValidTransition(BillStatus.VOID));
    }

    @Test
    void void_cannot_change() {
        var history = new StatusHistory(historyWith(BillStatus.VOID));

        assertThrows(IllegalArgumentException.class, () -> history.checkValidTransition(BillStatus.OPEN));
        assertThrows(IllegalArgumentException.class, () -> history.checkValidTransition(BillStatus.FROZEN));
        assertThrows(IllegalArgumentException.class, () -> history.checkValidTransition(BillStatus.CLOSED));
    }

    @Test
    void any_status_can_change_to_itself() {
        var history = new StatusHistory(historyWith(BillStatus.OPEN));
        history.checkValidTransition(BillStatus.OPEN);

        history = new StatusHistory(historyWith(BillStatus.FROZEN));
        history.checkValidTransition(BillStatus.FROZEN);

        history = new StatusHistory(historyWith(BillStatus.CLOSED));
        history.checkValidTransition(BillStatus.CLOSED);

        history = new StatusHistory(historyWith(BillStatus.VOID));
        history.checkValidTransition(BillStatus.VOID);
    }

    @Test
    void it_validates_status_history_in_constructor() {
        assertThrows(IllegalArgumentException.class, () -> new StatusHistory(historyWith(BillStatus.FROZEN, BillStatus.OPEN)));
        assertThrows(IllegalArgumentException.class, () -> new StatusHistory(historyWith(BillStatus.CLOSED, BillStatus.OPEN)));
        assertThrows(IllegalArgumentException.class, () -> new StatusHistory(historyWith(BillStatus.CLOSED, BillStatus.FROZEN)));
        assertThrows(IllegalArgumentException.class, () -> new StatusHistory(historyWith(BillStatus.CLOSED, BillStatus.VOID)));
        assertThrows(IllegalArgumentException.class, () -> new StatusHistory(historyWith(BillStatus.VOID, BillStatus.OPEN)));
        assertThrows(IllegalArgumentException.class, () -> new StatusHistory(historyWith(BillStatus.VOID, BillStatus.FROZEN)));
        assertThrows(IllegalArgumentException.class, () -> new StatusHistory(historyWith(BillStatus.VOID, BillStatus.CLOSED)));
    }

    private SortedMap<ZonedDateTime, BillStatus> historyWith(BillStatus... statuses) {
        var history = new TreeMap<>(Map.of(ZonedDateTime.now(clock), BillStatus.OPEN));
        for (var status : statuses) {
            history.put(ZonedDateTime.now(clock), status);
        }
        return history;
    }
}
