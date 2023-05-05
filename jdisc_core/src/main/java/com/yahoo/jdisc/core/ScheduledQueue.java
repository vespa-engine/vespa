// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import java.util.Objects;
import java.util.Queue;

/**
 * @author <a href="mailto:havardpe@yahoo-inc.com">Haavard Pettersen</a>
 * @author Simon Thoresen Hult
 */
class ScheduledQueue {

    public static final int MILLIS_PER_SLOT = 2;
    public static final int NUM_SLOTS = 1024;
    public static final int SLOT_MASK = NUM_SLOTS - 1; // bitmask to modulo NUM_SLOTS
    public static final int ITER_SHIFT = Integer.numberOfTrailingZeros(NUM_SLOTS); // number of bits to shift off SLOT_MASK

    private final Entry[] slots = new Entry[NUM_SLOTS + 1];
    private final int[] counts = new int[NUM_SLOTS + 1];
    private int currIter = 0;
    private int currSlot = 0;
    private long nextTick;

    public ScheduledQueue(long currentTimeMillis) {
        this.nextTick = currentTimeMillis + MILLIS_PER_SLOT;
    }

    public Entry newEntry(Object payload) {
        Objects.requireNonNull(payload, "payload");
        return new Entry(payload);
    }

    public synchronized void drainTo(long currentTimeMillis, Queue<Object> out) {
        if (slots[NUM_SLOTS] == null && currentTimeMillis < nextTick) {
            return;
        }
        drainTo(NUM_SLOTS, 0, out);
        while (currentTimeMillis >= nextTick) {
            if (++currSlot >= NUM_SLOTS) {
                currSlot = 0;
                currIter++;
            }
            drainTo(currSlot, currIter, out);
            nextTick += MILLIS_PER_SLOT;
        }

    }

    private void drainTo(int slot, int iter, Queue<Object> out) {
        int cnt = counts[slot];
        Entry entry = slots[slot];
        for (int i = 0; i < cnt; i++) {
            Entry next = entry.next;
            if (entry.iter == iter) {
                linkOut(entry);
                out.add(entry.payload);
            }
            entry = next;
        }
    }

    synchronized int queueSize() {
        int sum = 0;
        for (int cnt : counts) {
            sum += cnt;
        }
        return sum;
    }

    private synchronized void scheduleAt(Entry entry, long expireAtMillis) {
        if (entry.next != null) {
            linkOut(entry);
        }
        long delayMillis = expireAtMillis - nextTick;
        if (delayMillis < 0) {
            entry.slot = NUM_SLOTS;
            entry.iter = 0;
        } else {
            long ticks = 1 + (int)((delayMillis + MILLIS_PER_SLOT / 2) / MILLIS_PER_SLOT);
            entry.slot = (int)((ticks + currSlot) & SLOT_MASK);
            entry.iter = currIter + (int)((ticks + currSlot) >> ITER_SHIFT);
        }
        linkIn(entry);
    }

    private synchronized void unschedule(Entry entry) {
        if (entry.next != null) {
            linkOut(entry);
        }
    }

    private void linkIn(Entry entry) {
        Entry head = slots[entry.slot];
        if (head == null) {
            entry.next = entry;
            entry.prev = entry;
            slots[entry.slot] = entry;
        } else {
            entry.next = head;
            entry.prev = head.prev;
            head.prev.next = entry;
            head.prev = entry;
        }
        ++counts[entry.slot];
    }

    private void linkOut(Entry entry) {
        Entry head = slots[entry.slot];
        if (entry.next == entry) {
            slots[entry.slot] = null;
        } else {
            entry.prev.next = entry.next;
            entry.next.prev = entry.prev;
            if (head == entry) {
                slots[entry.slot] = entry.next;
            }
        }
        entry.next = null;
        entry.prev = null;
        --counts[entry.slot];
    }

    public class Entry {

        private final Object payload;
        private int slot;
        private int iter;
        private Entry next;
        private Entry prev;

        private Entry(Object payload) {
            this.payload = payload;
        }

        public void scheduleAt(long expireAtMillis) {
            ScheduledQueue.this.scheduleAt(this, expireAtMillis);
        }

        public void unschedule() {
            ScheduledQueue.this.unschedule(this);
        }
    }
}
