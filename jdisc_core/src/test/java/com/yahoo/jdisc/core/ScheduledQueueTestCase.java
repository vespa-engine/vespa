// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static com.yahoo.jdisc.core.ScheduledQueue.MILLIS_PER_SLOT;
import static com.yahoo.jdisc.core.ScheduledQueue.NUM_SLOTS;
import static com.yahoo.jdisc.core.ScheduledQueue.NUM_SLOTS_UNDILATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class ScheduledQueueTestCase {

    @Test
    void requireThatSlotMaskPreventsOverflow() {
        for (int slot = 0; slot < NUM_SLOTS * 2; ++slot) {
            assertTrue((slot & ScheduledQueue.SLOT_MASK) < NUM_SLOTS);
        }
    }

    @Test
    void requireThatIterShiftDiscardsSlotBits() {
        for (int slot = 0; slot < NUM_SLOTS * 2; ++slot) {
            assertEquals(slot / NUM_SLOTS, slot >> ScheduledQueue.ITER_SHIFT);
        }
    }

    @Test
    void requireThatNewEntryDoesNotAcceptNull() {
        ScheduledQueue queue = new ScheduledQueue(0);
        try {
            queue.newEntry(null);
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    void requireThatEntriesCanBeScheduled() {
        ScheduledQueue queue = new ScheduledQueue(0);
        Object foo = new Object();
        ScheduledQueue.Entry entry = queue.newEntry(foo);
        entry.scheduleAt(200);

        assertDrainTo(queue, 150);
        assertDrainTo(queue, 250, foo);
    }

    @Test
    void requireThatEntriesCanBeRescheduled() {
        ScheduledQueue queue = new ScheduledQueue(0);
        Object foo = new Object();
        ScheduledQueue.Entry entry = queue.newEntry(foo);
        entry.scheduleAt(200);
        entry.scheduleAt(100);

        assertDrainTo(queue, 150, foo);
        assertDrainTo(queue, 250);
    }

    @Test
    void requireThatEntriesCanBeUnscheduled() {
        ScheduledQueue queue = new ScheduledQueue(0);
        Object foo = new Object();
        ScheduledQueue.Entry entry = queue.newEntry(foo);
        entry.scheduleAt(100);
        entry.unschedule();

        assertDrainTo(queue, 150);
    }

    @Test
    void requireThatDrainToOnlyDrainsExpiredEntries() {
        ScheduledQueue queue = new ScheduledQueue(0);
        Object foo = scheduleAt(queue, 100);
        Object bar = scheduleAt(queue, 300);
        Object baz = scheduleAt(queue, 200);

        assertDrainTo(queue, 150, foo);
        assertDrainTo(queue, 250, baz);
        assertDrainTo(queue, 350, bar);
        assertDrainTo(queue, 450);
    }

    @Test
    void requireThatEntriesDoNotExpireMoreThanOnce() {
        ScheduledQueue queue = new ScheduledQueue(0);
        Object foo = scheduleAt(queue, NUM_SLOTS * MILLIS_PER_SLOT + 50);

        long now = 0;
        for (int i = 0; i < NUM_SLOTS; ++i, now += MILLIS_PER_SLOT) {
            assertDrainTo(queue, now);
        }
        assertDrainTo(queue, now += MILLIS_PER_SLOT, foo);
        for (int i = 0; i < NUM_SLOTS; ++i, now += MILLIS_PER_SLOT) {
            assertDrainTo(queue, now);
        }
    }

    @Test
    void requireThatNegativeScheduleTranslatesToNow() {
        ScheduledQueue queue = new ScheduledQueue(0);
        Object foo = scheduleAt(queue, -100);

        assertDrainTo(queue, 0, foo);
    }

    @Test
    void requireThatDrainToPerformsTimeDilationWhenOverloaded() {
        ScheduledQueue queue = new ScheduledQueue(0);
        List<Object> payloads = new LinkedList<>();
        for (int i = 1; i <= NUM_SLOTS_UNDILATED + 1; ++i) {
            payloads.add(scheduleAt(queue, i * MILLIS_PER_SLOT));
        }

        Queue<Object> expired = new LinkedList<>();
        long currentTimeMillis = payloads.size() * MILLIS_PER_SLOT;
        queue.drainTo(currentTimeMillis, expired);
        assertEquals(NUM_SLOTS_UNDILATED, expired.size());

        expired = new LinkedList<>();
        currentTimeMillis += MILLIS_PER_SLOT;
        queue.drainTo(currentTimeMillis, expired);
        assertEquals(1, expired.size());
    }

    private static Object scheduleAt(ScheduledQueue queue, long expireAtMillis) {
        Object obj = new Object();
        queue.newEntry(obj).scheduleAt(expireAtMillis);
        return obj;
    }

    private static void assertDrainTo(ScheduledQueue queue, long currentTimeMillis, Object... expected) {
        Queue<Object> expired = new LinkedList<>();
        queue.drainTo(currentTimeMillis, expired);
        assertEquals(expected.length, expired.size());
        assertEquals(Arrays.asList(expected), expired);
    }
}
