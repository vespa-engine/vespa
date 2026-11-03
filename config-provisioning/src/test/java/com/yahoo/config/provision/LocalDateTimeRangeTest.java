// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bragehk
 */
class LocalDateTimeRangeTest {

    @Test
    void unbounded_range_includes_everything() {
        LocalDateTimeRange range = new LocalDateTimeRange(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        assertTrue(range.includes(LocalDateTime.MIN));
        assertTrue(range.includes(LocalDateTime.MAX));
        assertTrue(range.includes(LocalDateTime.of(2000, 1, 1, 0, 0)));
    }

    @Test
    void date_only_bounds_default_to_start_and_end_of_day() {
        LocalDateTimeRange range = new LocalDateTimeRange(Optional.of(LocalDate.of(2026, 1, 1)), Optional.empty(),
                                                            Optional.of(LocalDate.of(2026, 1, 3)), Optional.empty());
        assertFalse(range.includes(LocalDateTime.of(2025, 12, 31, 23, 59)));
        assertTrue(range.includes(LocalDateTime.of(2026, 1, 1, 0, 0)));
        assertTrue(range.includes(LocalDateTime.of(2026, 1, 3, 23, 59, 59)));
        assertFalse(range.includes(LocalDateTime.of(2026, 1, 4, 0, 0)));
    }

    @Test
    void hour_granular_bounds_are_inclusive() {
        LocalDateTimeRange range = new LocalDateTimeRange(Optional.of(LocalDate.of(2026, 1, 1)), Optional.of(LocalTime.of(9, 0)),
                                                            Optional.of(LocalDate.of(2026, 1, 1)), Optional.of(LocalTime.of(17, 0)));
        assertFalse(range.includes(LocalDateTime.of(2026, 1, 1, 8, 59)));
        assertTrue(range.includes(LocalDateTime.of(2026, 1, 1, 9, 0)));
        assertTrue(range.includes(LocalDateTime.of(2026, 1, 1, 17, 0)));
        assertFalse(range.includes(LocalDateTime.of(2026, 1, 1, 17, 1)));
    }

    @Test
    void rejects_inverted_range() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new LocalDateTimeRange(
                Optional.of(LocalDate.of(2026, 1, 5)), Optional.empty(),
                Optional.of(LocalDate.of(2026, 1, 1)), Optional.empty()));
        assertEquals("Invalid date range: start date 2026-01-05 is after end date 2026-01-01", e.getMessage());
    }

    @Test
    void rejects_inverted_range_on_same_date_with_times() {
        assertThrows(IllegalArgumentException.class, () -> new LocalDateTimeRange(
                Optional.of(LocalDate.of(2026, 1, 1)), Optional.of(LocalTime.of(17, 0)),
                Optional.of(LocalDate.of(2026, 1, 1)), Optional.of(LocalTime.of(9, 0))));
    }

}
