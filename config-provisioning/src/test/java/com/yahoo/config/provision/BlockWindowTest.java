// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bragehk
 */
class BlockWindowTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void blocks_within_hour_granular_date_range() {
        BlockWindow window = new BlockWindow(true, true, true,
                                              List.of(DayOfWeek.MONDAY), List.of(9, 10, 11), UTC,
                                              Optional.of(LocalDate.of(2026, 1, 5)), Optional.of(LocalDate.of(2026, 1, 5)),
                                              Optional.of(LocalTime.of(9, 30)), Optional.of(LocalTime.of(10, 30)));

        // Before the from-time on the from-date
        assertFalse(window.blocksPlatformAt(Instant.parse("2026-01-05T09:00:00Z")));
        // At the from-time boundary (inclusive)
        assertTrue(window.blocksPlatformAt(Instant.parse("2026-01-05T09:30:00Z")));
        // Between the bounds
        assertTrue(window.blocksPlatformAt(Instant.parse("2026-01-05T10:00:00Z")));
        // At the to-time boundary (inclusive)
        assertTrue(window.blocksPlatformAt(Instant.parse("2026-01-05T10:30:00Z")));
        // After the to-time on the to-date
        assertFalse(window.blocksPlatformAt(Instant.parse("2026-01-05T10:31:00Z")));
    }

    @Test
    void blocks_all_flags_only_when_enabled() {
        BlockWindow window = new BlockWindow(true, false, false, List.of(DayOfWeek.MONDAY), List.of(9), UTC);
        Instant withinWindow = Instant.parse("2026-01-05T09:30:00Z"); // A Monday

        assertTrue(window.blocksRevisionAt(withinWindow));
        assertFalse(window.blocksPlatformAt(withinWindow));
        assertFalse(window.blocksMaintenanceAt(withinWindow));
    }

    @Test
    void does_not_block_outside_days_or_hours() {
        BlockWindow window = new BlockWindow(true, true, true, List.of(DayOfWeek.MONDAY), List.of(9), UTC);

        assertFalse(window.blocksPlatformAt(Instant.parse("2026-01-06T09:30:00Z"))); // Tuesday
        assertFalse(window.blocksPlatformAt(Instant.parse("2026-01-05T10:30:00Z"))); // Wrong hour
    }

    @Test
    void rejects_inverted_date_range() {
        assertThrows(IllegalArgumentException.class, () -> new BlockWindow(
                true, true, true, List.of(DayOfWeek.MONDAY), List.of(9), UTC,
                Optional.of(LocalDate.of(2026, 1, 5)), Optional.of(LocalDate.of(2026, 1, 1))));
    }

    @Test
    void rejects_inverted_date_range_with_same_date_but_inverted_times() {
        assertThrows(IllegalArgumentException.class, () -> new BlockWindow(
                true, true, true, List.of(DayOfWeek.MONDAY), List.of(9), UTC,
                Optional.of(LocalDate.of(2026, 1, 5)), Optional.of(LocalDate.of(2026, 1, 5)),
                Optional.of(LocalTime.of(10, 0)), Optional.of(LocalTime.of(9, 0))));
    }

    @Test
    void days_and_hours_are_immutable() {
        List<DayOfWeek> days = new java.util.ArrayList<>(List.of(DayOfWeek.MONDAY));
        List<Integer> hours = new java.util.ArrayList<>(List.of(9));
        BlockWindow window = new BlockWindow(true, true, true, days, hours, UTC);
        days.add(DayOfWeek.TUESDAY);
        hours.add(10);

        assertEquals(List.of(DayOfWeek.MONDAY), window.days());
        assertEquals(List.of(9), window.hours());
    }

}
