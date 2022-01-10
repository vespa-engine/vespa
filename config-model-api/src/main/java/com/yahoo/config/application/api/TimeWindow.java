// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class represents a window of time for selected hours, days and dates.
 *
 * @author mpolden
 */
public class TimeWindow {

    private final List<DayOfWeek> days;
    private final List<Integer> hours;
    private final ZoneId zone;
    private final LocalDateRange dateRange;

    private TimeWindow(List<DayOfWeek> days, List<Integer> hours, ZoneId zone, LocalDateRange dateRange) {
        this.days = Objects.requireNonNull(days).stream().distinct().sorted().collect(Collectors.toUnmodifiableList());
        this.hours = Objects.requireNonNull(hours).stream().distinct().sorted().collect(Collectors.toUnmodifiableList());
        this.zone = Objects.requireNonNull(zone);
        this.dateRange = Objects.requireNonNull(dateRange);
        if (days.isEmpty()) throw new IllegalArgumentException("At least one day must be specified");
        if (hours.isEmpty()) throw new IllegalArgumentException("At least one hour must be specified");
        for (var day : days) {
            if (!dateRange.days().contains(day)) {
                throw new IllegalArgumentException("Invalid day: " + dateRange + " does not contain " + day);
            }
        }
    }

    /** Returns days in this time window */
    public List<DayOfWeek> days() {
        return days;
    }

    /** Returns hours in this time window */
    public List<Integer> hours() {
        return hours;
    }

    /** Returns the time zone of this time window */
    public ZoneId zone() { return zone;  }

    /** Returns the date range of this time window applies to */
    public LocalDateRange dateRange() {
        return dateRange;
    }

    /** Returns whether the given instant is in this time window */
    public boolean includes(Instant instant) {
        LocalDateTime dt = LocalDateTime.ofInstant(instant, zone);
        return days.contains(dt.getDayOfWeek()) &&
               hours.contains(dt.getHour()) &&
               dateRange.includes(dt.toLocalDate());
    }

    @Override
    public String toString() {
        return "time window for hour(s) " +
               hours.toString() +
               " on " + days.stream().map(DayOfWeek::name)
                            .map(String::toLowerCase)
                            .collect(Collectors.toList()) +
               " in time zone " + zone + " and " + dateRange.toString();
    }

    /** Parse a time window from the given day, hour and time zone specification */
    public static TimeWindow from(String daySpec, String hourSpec, String zoneSpec, String dateStart, String dateEnd) {
        LocalDateRange dateRange = LocalDateRange.from(dateStart, dateEnd);
        List<DayOfWeek> days = daySpec.isEmpty()
                ? List.copyOf(dateRange.days()) // Default to the days contained in the date range
                : parse(daySpec, TimeWindow::parseDays);
        List<Integer> hours = hourSpec.isEmpty()
                ? IntStream.rangeClosed(0, 23).boxed().collect(Collectors.toList()) // All hours by default
                : parse(hourSpec, TimeWindow::parseHours);
        ZoneId zone = zoneFrom(zoneSpec.isEmpty() ? "UTC" : zoneSpec);
        return new TimeWindow(days, hours, zone, dateRange);
    }

    /** Parse a specification, e.g. "1,4-5", using the given value parser */
    private static <T> List<T> parse(String spec, BiFunction<String, String, List<T>> valueParser) {
        List<T> values = new ArrayList<>();
        String[] parts = spec.split(",");
        for (String part : parts) {
            if (part.contains("-")) {
                String[] startAndEnd = part.split("-");
                if (startAndEnd.length != 2) {
                    throw new IllegalArgumentException("Invalid range '" + part + "'");
                }
                values.addAll(valueParser.apply(startAndEnd[0], startAndEnd[1]));
            } else {
                values.addAll(valueParser.apply(part, part));
            }
        }
        return Collections.unmodifiableList(values);
    }

    /** Returns a list of all hours occurring between startInclusive and endInclusive */
    private static List<Integer> parseHours(String startInclusive, String endInclusive) {
        int start = hourFrom(startInclusive);
        int end = hourFrom(endInclusive);
        if (end < start) {
            throw new IllegalArgumentException(String.format("Invalid hour range '%s-%s'", startInclusive,
                                                             endInclusive));
        }
        return IntStream.rangeClosed(start, end).boxed()
                        .collect(Collectors.toList());
    }

    /** Returns a list of all days occurring between startInclusive and endInclusive */
    private static List<DayOfWeek> parseDays(String startInclusive, String endInclusive) {
        DayOfWeek start = dayFrom(startInclusive);
        DayOfWeek end = dayFrom(endInclusive);
        if (end.getValue() < start.getValue()) {
            throw new IllegalArgumentException(String.format("Invalid day range '%s-%s'", startInclusive,
                                                             endInclusive));
        }
        return IntStream.rangeClosed(start.getValue(), end.getValue()).boxed()
                        .map(DayOfWeek::of)
                        .collect(Collectors.toList());
    }

    /** Parse day of week from string */
    private static DayOfWeek dayFrom(String day) {
        return Arrays.stream(DayOfWeek.values())
                     .filter(dayOfWeek -> day.length() >= 3 && dayOfWeek.name().toLowerCase().startsWith(day))
                     .findFirst()
                     .orElseThrow(() -> new IllegalArgumentException("Invalid day '" + day + "'"));
    }

    /** Parse hour from string */
    private static int hourFrom(String hour) {
        try {
            return ChronoField.HOUR_OF_DAY.checkValidIntValue(Integer.parseInt(hour));
        } catch (DateTimeException|NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hour '" + hour + "'", e);
        }
    }

    /** Parse time zone from string */
    private static ZoneId zoneFrom(String zone) {
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid time zone '" + zone + "'", e);
        }
    }

    /** A range of local dates, which may be unbounded */
    public static class LocalDateRange {

        private final Optional<LocalDate> start;
        private final Optional<LocalDate> end;

        private LocalDateRange(Optional<LocalDate> start, Optional<LocalDate> end) {
            this.start = Objects.requireNonNull(start);
            this.end = Objects.requireNonNull(end);
            if (start.isPresent() && end.isPresent() && start.get().isAfter(end.get())) {
                throw new IllegalArgumentException("Invalid date range: start date " + start.get() +
                                                   " is after end date " + end.get());
            }
        }

        /** Returns the starting date of this (inclusive), if any */
        public Optional<LocalDate> start() {
            return start;
        }

        /** Returns the ending date of this (inclusive), if any */
        public Optional<LocalDate> end() {
            return end;
        }

        /** Return days of week found in this range */
        private Set<DayOfWeek> days() {
            if (start.isEmpty() || end.isEmpty()) return EnumSet.allOf(DayOfWeek.class);
            Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            for (LocalDate date = start.get(); !date.isAfter(end.get()) && days.size() < 7; date = date.plusDays(1)) {
                days.add(date.getDayOfWeek());
            }
            return days;
        }

        /** Returns whether includes the given date */
        private boolean includes(LocalDate date) {
            if (start.isPresent() && date.isBefore(start.get())) return false;
            if (end.isPresent() && date.isAfter(end.get())) return false;
            return true;
        }

        @Override
        public String toString() {
            return "date range [" + start.map(LocalDate::toString).orElse("any date") +
                   ", " + end.map(LocalDate::toString).orElse("any date") + "]";
        }

        private static LocalDateRange from(String start, String end) {
            try {
                return new LocalDateRange(optionalDate(start), optionalDate(end));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Could not parse date range '" + start + "' and '" + end + "'", e);
            }
        }

        private static Optional<LocalDate> optionalDate(String date) {
            return Optional.of(date).filter(s -> !s.isEmpty()).map(LocalDate::parse);
        }

    }

}
