// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;


import java.util.LinkedList;
import java.util.List;

/**
 * Reference implementation of the 'Incremental Minimal Event Barrier'
 * algorithm. An event in this context is defined to be something that
 * happens during a time interval. An event barrier is a time interval
 * for which events may start before or end after, but not both. The
 * problem solved by the algorithm is to determine the minimal event
 * barrier starting at a given time. In other words; wait for the
 * currently active events to complete. The most natural use of this
 * algorithm would be to make a thread wait for events happening in
 * other threads to complete.
 *
 * @author Haavard Pettersen
 * @author Simon Thoresen Hult
 */
public class EventBarrier {

    private final List<Entry> queue = new LinkedList<>();
    private int barrierToken = 0;
    private int eventCount = 0;

    /**
     * At creation there are no active events and no pending barriers.
     */
    public EventBarrier() {
        // empty
    }

    /**
     * Obtain the current number of active events. This method is
     * intended for testing and debugging.
     *
     * @return Number of active events.
     */
    int getNumEvents() {
        int cnt = eventCount;
        for (Entry entry : queue) {
            cnt += entry.eventCount;
        }
        return cnt;
    }

    /**
     * Obtain the current number of pending barriers. This method is
     * intended for testing and debugging.
     *
     * @return Number of pending barriers.
     */
    int getNumBarriers() {
        return queue.size();
    }

    /**
     * Signal the start of an event. The value returned from this
     * method must later be passed to the completeEvent method when
     * signaling the completion of the event.
     *
     * @return Opaque token identifying the started event.
     */
    public int startEvent() {
        ++eventCount;
        return barrierToken;
    }

    /**
     * Signal the completion of an event. The value passed to this
     * method must be the same as the return value previously obtained
     * from the startEvent method. This method will signal the
     * completion of all pending barriers that were completed by the
     * completion of this event.
     *
     * @param token Opaque token identifying the completed event.
     */
    public void completeEvent(int token) {
        if (token == this.barrierToken) {
            --eventCount;
            return;
        }
        --queue.get(queue.size() - (this.barrierToken - token)).eventCount;
        while (!queue.isEmpty() && queue.get(0).eventCount == 0) {
            queue.remove(0).handler.completeBarrier();
        }
    }

    /**
     * Initiate the detection of the minimal event barrier starting
     * now. If this method returns false it means that no events were
     * currently active and the minimal event barrier was infinitely
     * small. If this method returns false the handler will not be
     * notified of the completion of the barrier. If this method
     * returns true it means that the started barrier is pending and
     * that the handler passed to this method will be notified of its
     * completion at a later time.
     *
     * @param handler Handler notified of the completion of the barrier.
     * @return True if a barrier was started, false if no events were active.
     */
    public boolean startBarrier(BarrierWaiter handler) {
        if (eventCount == 0 && queue.isEmpty()) {
            return false;
        }
        queue.add(new Entry(eventCount, handler));
        ++barrierToken;
        eventCount = 0;
        return true;
    }

    /**
     * Declares the interface required to wait for the detection of a
     * minimal event barrier. An object that implements this is passed
     * to the {@link EventBarrier#startBarrier(BarrierWaiter)}.
     */
    public interface BarrierWaiter {

        /**
         * Callback invoked by the thread that detected the minimal
         * event barrier. Once this is called, all events taking place
         * at or before the corresponding call to {@link
         * EventBarrier#startBarrier(BarrierWaiter)} have ended.
         */
        public void completeBarrier();
    }

    private static class Entry {

        int eventCount;
        final BarrierWaiter handler;

        Entry(int eventCount, BarrierWaiter handler) {
            this.eventCount = eventCount;
            this.handler = handler;
        }
    }
}
