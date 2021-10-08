// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class represents a window of time for selected hours on selected days.
 *
 * @author mpolden
 */
public class TimeWindow {

    private final List<DayOfWeek> days;
    private final List<Integer> hours;
    private final ZoneId zone;

    private TimeWindow(List<DayOfWeek> days, List<Integer> hours, ZoneId zone) {
        this.days = Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(days)));
        this.hours = Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(hours)));
        this.zone = zone;
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

    /** Returns whether the given instant is in this time window */
    public boolean includes(Instant instant) {
        LocalDateTime dt = LocalDateTime.ofInstant(instant, zone);
        return days.contains(dt.getDayOfWeek()) && hours.contains(dt.getHour());
    }

    @Override
    public String toString() {
        return "time window for hour(s) " +
               hours.toString() +
               " on " + days.stream().map(DayOfWeek::name)
                       .map(String::toLowerCase)
                       .collect(Collectors.toList()).toString() +
               " in " + zone;
    }

    /** Parse a time window from the given day, hour and time zone specification */
    public static TimeWindow from(String daySpec, String hourSpec, String zoneSpec) {
        List<DayOfWeek> days = parse(daySpec, TimeWindow::parseDays);
        List<Integer> hours = parse(hourSpec, TimeWindow::parseHours);
        ZoneId zone = zoneFrom(zoneSpec);
        return new TimeWindow(days, hours, zone);
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

}
