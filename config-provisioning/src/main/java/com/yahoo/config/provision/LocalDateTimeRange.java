// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

/**
 * A range of local date-times, bounded by an optional start and end date, each optionally carrying
 * a time of day. A missing start or end date means the range is unbounded in that direction. A date
 * without a time defaults to the start (for the start date) or end (for the end date) of that day.
 *
 * @author bragehk
 */
public record LocalDateTimeRange(Optional<LocalDate> startDate, Optional<LocalTime> startTime,
                                  Optional<LocalDate> endDate, Optional<LocalTime> endTime) {

    public LocalDateTimeRange {
        Objects.requireNonNull(startDate);
        Objects.requireNonNull(startTime);
        Objects.requireNonNull(endDate);
        Objects.requireNonNull(endTime);
        if (startDate.isPresent() && endDate.isPresent()) {
            LocalDateTime start = startDate.get().atTime(startTime.orElse(LocalTime.MIN));
            LocalDateTime end = endDate.get().atTime(endTime.orElse(LocalTime.MAX));
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("Invalid date range: start date " + boundString(startDate, startTime) +
                                                    " is after end date " + boundString(endDate, endTime));
            }
        }
    }

    /** Returns whether this range includes the given date and time */
    public boolean includes(LocalDateTime dateTime) {
        if (startDate.isPresent() && dateTime.isBefore(startInclusive())) return false;
        if (endDate.isPresent() && dateTime.isAfter(endInclusive())) return false;
        return true;
    }

    /** Returns the earliest point in time included by this, or the minimum possible point in time if unbounded */
    public LocalDateTime startInclusive() {
        return startDate.map(date -> date.atTime(startTime.orElse(LocalTime.MIN))).orElse(LocalDateTime.MIN);
    }

    /** Returns the latest point in time included by this, or the maximum possible point in time if unbounded */
    public LocalDateTime endInclusive() {
        return endDate.map(date -> date.atTime(endTime.orElse(LocalTime.MAX))).orElse(LocalDateTime.MAX);
    }

    public static String boundString(Optional<LocalDate> date, Optional<LocalTime> time) {
        return date.map(d -> time.<String>map(t -> d + "T" + t).orElseGet(d::toString))
                   .orElse("any date");
    }

}
