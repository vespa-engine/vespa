// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author mpolden
 */
public class TimeWindowTest {

    @Test
    public void includesInstant() {
        {
            TimeWindow tw = TimeWindow.from("mon", "10,11", "UTC", "", "");
            Instant i0 = Instant.parse("2017-09-17T11:15:30.00Z"); // Wrong day
            Instant i1 = Instant.parse("2017-09-18T09:15:30.00Z"); // Wrong hour
            Instant i2 = Instant.parse("2017-09-18T10:15:30.00Z");
            Instant i3 = Instant.parse("2017-09-18T11:15:30.00Z");
            Instant i4 = Instant.parse("2017-09-18T12:15:30.00Z"); // Wrong hour
            Instant i5 = Instant.parse("2017-09-19T11:15:30.00Z"); // Wrong day
            assertOutside(tw, i0);
            assertOutside(tw, i1);
            assertInside(tw, i2);
            assertInside(tw, i3);
            assertOutside(tw, i4);
            assertOutside(tw, i5);
        }
        {
            TimeWindow tw = TimeWindow.from("mon", "12,13", "CET", "", "");
            Instant i0 = Instant.parse("2017-09-17T11:15:30.00Z");
            Instant i1 = Instant.parse("2017-09-18T09:15:30.00Z");
            Instant i2 = Instant.parse("2017-09-18T10:15:30.00Z"); // Including offset this matches hour 12
            Instant i3 = Instant.parse("2017-09-18T11:15:30.00Z"); // Including offset this matches hour 13
            Instant i4 = Instant.parse("2017-09-18T12:15:30.00Z");
            Instant i5 = Instant.parse("2017-09-19T11:15:30.00Z");
            assertOutside(tw, i0);
            assertOutside(tw, i1);
            assertInside(tw, i2);
            assertInside(tw, i3);
            assertOutside(tw, i4);
            assertOutside(tw, i5);
        }
        {
            TimeWindow tw = TimeWindow.from("mon-sun", "0-23", "CET", "2022-01-15", "2022-02-15");
            Instant i0 = Instant.parse("2022-01-14T12:00:00.00Z"); // Before window
            Instant i1 = Instant.parse("2022-01-14T23:00:00.00Z"); // Inside window because of time zone offset
            Instant i2 = Instant.parse("2022-02-05T12:00:00.00Z");
            Instant i3 = Instant.parse("2022-02-14T23:00:00.00Z");
            Instant i4 = Instant.parse("2022-02-15T23:00:00.00Z"); // After window because of time zone offset
            Instant i5 = Instant.parse("2022-02-16T12:00:00.00Z"); // After window
            assertOutside(tw, i0);
            assertInside(tw, i1);
            assertInside(tw, i2);
            assertInside(tw, i3);
            assertOutside(tw, i4);
            assertOutside(tw, i5);

            TimeWindow tw2 = TimeWindow.from("sun", "1", "CET", "2022-01-01", "2022-01-02");
            Instant i6 = Instant.parse("2022-01-01T00:00:00.00Z"); // Wrong day
            Instant i7 = Instant.parse("2022-01-02T01:00:00.00Z"); // Wrong hour because of time zone offset
            Instant i8 = Instant.parse("2022-01-02T00:00:00.00Z");
            assertOutside(tw2, i6);
            assertOutside(tw2, i7);
            assertInside(tw2, i8);

            TimeWindow tw3 = TimeWindow.from("", "", "CET", "2022-01-02", "");
            Instant i9 = Instant.parse("2022-02-15T00:00:00.00Z");
            assertOutside(tw3, i6);
            assertInside(tw3, i7);
            assertInside(tw3, i8);
            assertInside(tw3, i9);
        }
    }

    @Test
    public void validWindows() {
        {
            TimeWindow tw = TimeWindow.from("fri", "8,17-19", "UTC", "", "");
            assertEquals(List.of(FRIDAY), tw.days());
            assertEquals(List.of(8, 17, 18, 19), tw.hours());
        }
        {
            TimeWindow tw = TimeWindow.from("sat,", "8,17-19", "UTC", "", "");
            assertEquals(List.of(SATURDAY), tw.days());
            assertEquals(List.of(8, 17, 18, 19), tw.hours());
        }
        {
            TimeWindow tw = TimeWindow.from("tue,sat", "0,3,7,10", "UTC", "", "");
            assertEquals(List.of(TUESDAY, SATURDAY), tw.days());
            assertEquals(List.of(0, 3, 7, 10), tw.hours());
        }
        {
            TimeWindow tw = TimeWindow.from("mon,wed-thu", "0,17-19", "UTC", "", "");
            assertEquals(List.of(MONDAY, WEDNESDAY, THURSDAY), tw.days());
            assertEquals(List.of(0, 17, 18, 19), tw.hours());
        }
        { // Empty results in default values
            TimeWindow tw = TimeWindow.from("", "", "", "", "");
            assertEquals(List.of(DayOfWeek.values()), tw.days());
            assertEquals(IntStream.rangeClosed(0, 23).boxed().collect(Collectors.toList()), tw.hours());
            assertEquals("UTC", tw.zone().getId());
        }
        {
            // Full day names is allowed
            TimeWindow tw = TimeWindow.from("monday,wednesday-thursday", "0,17-19", "UTC", "", "");
            assertEquals(List.of(MONDAY, WEDNESDAY, THURSDAY), tw.days());
            assertEquals(List.of(0, 17, 18, 19), tw.hours());
        }
        {
            // Duplicate day and overlapping range is allowed
            TimeWindow tw = TimeWindow.from("mon,wed-thu,mon", "3,1-4", "UTC", "", "");
            assertEquals(List.of(MONDAY, WEDNESDAY, THURSDAY), tw.days());
            assertEquals(List.of(1, 2, 3, 4), tw.hours());
        }
        {   // Default to days contained in the date range
            TimeWindow tw = TimeWindow.from("", "", "", "2022-01-11", "2022-01-14");
            assertEquals(List.of(TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), tw.days());
            TimeWindow tw2 = TimeWindow.from("", "", "", "2022-01-01", "2100-01-01");
            assertEquals(List.of(DayOfWeek.values()), tw2.days());
        }
    }

    @Test
    public void invalidWindows() {
        // Invalid time zone
        assertInvalidZone("foo", "Invalid time zone 'foo'");

        // Malformed day input
        assertInvalidDays("foo-", "Invalid range 'foo-'");
        assertInvalidDays("foo", "Invalid day 'foo'");
        assertInvalidDays("f", "Invalid day 'f'");
        // Window crossing week boundary is disallowed
        assertInvalidDays("fri-tue", "Invalid day range 'fri-tue'");

        // Malformed hour input
        assertInvalidHours("24", "Invalid hour '24'");
        assertInvalidHours("-1-9", "Invalid range '-1-9'");
        // Window crossing day boundary is disallowed
        assertInvalidHours("23-1", "Invalid hour range '23-1'");

        // Invalid date range
        assertInvalidDateRange("", "foo", "bar", "Could not parse date range 'foo' and 'bar'");
        assertInvalidDateRange("", "2022-01-15", "2022-01-01", "Invalid date range: start date 2022-01-15 is after end date 2022-01-01");
        assertInvalidDateRange("wed", "2022-01-06", "2022-01-09", "Invalid day: date range [2022-01-06, 2022-01-09] does not contain WEDNESDAY");
        assertInvalidDateRange("mon-sun", "2022-01-03", "2022-01-07", "Invalid day: date range [2022-01-03, 2022-01-07] does not contain SATURDAY");
    }

    private static void assertOutside(TimeWindow window, Instant instant) {
        assertFalse("Instant " + instant + " is not in window", window.includes(instant));
    }

    private static void assertInside(TimeWindow window, Instant instant) {
        assertTrue("Instant " + instant + " is in window", window.includes(instant));
    }

    private static void assertInvalidZone(String zoneSpec, String exceptionMessage) {
        try {
            TimeWindow.from("mon", "1", zoneSpec, "", "");
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals(exceptionMessage, e.getMessage());
        }
    }

    private static void assertInvalidDays(String daySpec, String exceptionMessage) {
        try {
            TimeWindow.from(daySpec, "1", "UTC", "", "");
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals(exceptionMessage, e.getMessage());
        }
    }

    private static void assertInvalidHours(String hourSpec, String exceptionMessage) {
        try {
            TimeWindow.from("mon", hourSpec, "UTC", "", "");
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals(exceptionMessage, e.getMessage());
        }
    }

    private static void assertInvalidDateRange(String daySpec, String startDate, String endDate, String message) {
        try {
            TimeWindow.from(daySpec, "", "UTC", startDate, endDate);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals(message, e.getMessage());
        }
    }

}
