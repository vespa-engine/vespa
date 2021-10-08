// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import org.junit.Test;

import java.time.Instant;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static java.util.Arrays.asList;
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
            TimeWindow tw = TimeWindow.from("mon", "10,11", "UTC");
            Instant i0 = Instant.parse("2017-09-17T11:15:30.00Z"); // Wrong day
            Instant i1 = Instant.parse("2017-09-18T09:15:30.00Z"); // Wrong hour
            Instant i2 = Instant.parse("2017-09-18T10:15:30.00Z");
            Instant i3 = Instant.parse("2017-09-18T11:15:30.00Z");
            Instant i4 = Instant.parse("2017-09-18T12:15:30.00Z"); // Wrong hour
            Instant i5 = Instant.parse("2017-09-19T11:15:30.00Z"); // Wrong day

            assertFalse("Instant " + i0 + " is not in window", tw.includes(i0));
            assertFalse("Instant " + i1 + " is not in window", tw.includes(i1));
            assertTrue("Instant " + i2 + " is in window", tw.includes(i2));
            assertTrue("Instant " + i3 + " is in window", tw.includes(i3));
            assertFalse("Instant " + i4 + " is not in window", tw.includes(i4));
            assertFalse("Instant " + i5 + " is not in window", tw.includes(i5));
        }
        {
            TimeWindow tw = TimeWindow.from("mon", "12,13", "CET");
            Instant i0 = Instant.parse("2017-09-17T11:15:30.00Z");
            Instant i1 = Instant.parse("2017-09-18T09:15:30.00Z");
            Instant i2 = Instant.parse("2017-09-18T10:15:30.00Z"); // Including offset this matches hour 12
            Instant i3 = Instant.parse("2017-09-18T11:15:30.00Z"); // Including offset this matches hour 13
            Instant i4 = Instant.parse("2017-09-18T12:15:30.00Z");
            Instant i5 = Instant.parse("2017-09-19T11:15:30.00Z");
            assertFalse("Instant " + i0 + " is not in window", tw.includes(i0));
            assertFalse("Instant " + i1 + " is not in window", tw.includes(i1));
            assertTrue("Instant " + i2 + " is in window", tw.includes(i2));
            assertTrue("Instant " + i3 + " is in window", tw.includes(i3));
            assertFalse("Instant " + i4 + " is not in window", tw.includes(i4));
            assertFalse("Instant " + i5 + " is not in window", tw.includes(i5));
        }
    }

    @Test
    public void validWindows() {
        {
            TimeWindow fz = TimeWindow.from("fri", "8,17-19", "UTC");
            assertEquals(asList(FRIDAY), fz.days());
            assertEquals(asList(8, 17, 18, 19), fz.hours());
        }
        {
            TimeWindow fz = TimeWindow.from("sat,", "8,17-19", "UTC");
            assertEquals(asList(SATURDAY), fz.days());
            assertEquals(asList(8, 17, 18, 19), fz.hours());
        }
        {
            TimeWindow fz = TimeWindow.from("tue,sat", "0,3,7,10", "UTC");
            assertEquals(asList(TUESDAY, SATURDAY), fz.days());
            assertEquals(asList(0, 3, 7, 10), fz.hours());
        }
        {
            TimeWindow fz = TimeWindow.from("mon,wed-thu", "0,17-19", "UTC");
            assertEquals(asList(MONDAY, WEDNESDAY, THURSDAY), fz.days());
            assertEquals(asList(0, 17, 18, 19), fz.hours());
        }
        {
            // Full day names is allowed
            TimeWindow fz = TimeWindow.from("monday,wednesday-thursday", "0,17-19", "UTC");
            assertEquals(asList(MONDAY, WEDNESDAY, THURSDAY), fz.days());
            assertEquals(asList(0, 17, 18, 19), fz.hours());
        }
        {
            // Duplicate day and overlapping range is allowed
            TimeWindow fz = TimeWindow.from("mon,wed-thu,mon", "3,1-4", "UTC");
            assertEquals(asList(MONDAY, WEDNESDAY, THURSDAY), fz.days());
            assertEquals(asList(1, 2, 3, 4), fz.hours());
        }
    }

    @Test
    public void invalidWindows() {
        // Invalid time zone
        assertInvalidZone("foo", "Invalid time zone 'foo'");

        // Malformed day input
        assertInvalidDays("", "Invalid day ''");
        assertInvalidDays("foo-", "Invalid range 'foo-'");
        assertInvalidDays("foo", "Invalid day 'foo'");
        assertInvalidDays("f", "Invalid day 'f'");
        // Window crossing week boundary is disallowed
        assertInvalidDays("fri-tue", "Invalid day range 'fri-tue'");

        // Malformed hour input
        assertInvalidHours("", "Invalid hour ''");
        assertInvalidHours("24", "Invalid hour '24'");
        assertInvalidHours("-1-9", "Invalid range '-1-9'");
        // Window crossing day boundary is disallowed
        assertInvalidHours("23-1", "Invalid hour range '23-1'");
    }

    private static void assertInvalidZone(String zoneSpec, String exceptionMessage) {
        try {
            TimeWindow.from("mon", "1", zoneSpec);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals(exceptionMessage, e.getMessage());
        }
    }

    private static void assertInvalidDays(String daySpec, String exceptionMessage) {
        try {
            TimeWindow.from(daySpec, "1", "UTC");
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals(exceptionMessage, e.getMessage());
        }
    }

    private static void assertInvalidHours(String hourSpec, String exceptionMessage) {
        try {
            TimeWindow.from("mon", hourSpec, "UTC");
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals(exceptionMessage, e.getMessage());
        }
    }

}
