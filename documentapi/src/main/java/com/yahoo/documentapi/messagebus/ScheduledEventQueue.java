// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus;

import com.yahoo.concurrent.SystemTimer;
import com.yahoo.concurrent.Timer;

import java.util.*;
import java.util.concurrent.RejectedExecutionException;

/**
 * Simple, lightweight event scheduler that does not maintain any executor
 * threads of its own, but rather makes it the responsibility of the caller
 * to run the events as the queue hands them over.
 *
 * Fully thread safe for multiple readers and writers.
 */
public class ScheduledEventQueue {
    private final Set<Entry> tasks = new TreeSet<Entry>();
    private long sequenceCounter = 0;
    private Timer timer;
    private volatile boolean waiting = false;
    private volatile boolean shutdown = false;

    private static class Entry implements Comparable<Entry> {
        private Runnable task;
        private long timestamp;
        private long sequenceId;

        public Entry(Runnable task, long timestamp, long sequenceId) {
            this.task = task;
            this.timestamp = timestamp;
            this.sequenceId = sequenceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Entry entry = (Entry) o;

            if (sequenceId != entry.sequenceId) return false;
            if (timestamp != entry.timestamp) return false;
            if (!task.equals(entry.task)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(sequenceId, timestamp, task);
        }

        @Override
        public int compareTo(Entry o) {
            if (timestamp < o.timestamp) return -1;
            if (timestamp > o.timestamp) return 1;
            if (sequenceId < o.sequenceId) return -1;
            if (sequenceId > o.sequenceId) return 1;
            return 0;
        }

        public Runnable getTask() {
            return task;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public long getSequenceId() {
            return sequenceId;
        }
    }

    public ScheduledEventQueue() {
        this.timer = SystemTimer.INSTANCE;
    }

    public ScheduledEventQueue(Timer timer) {
        this.timer = timer;
    }

    public void pushTask(Runnable task) {
        synchronized (tasks) {
            if (shutdown) {
                throw new RejectedExecutionException("Tasks can't be scheduled since queue has been shut down.");
            }

            tasks.add(new Entry(task, 0, sequenceCounter++));
            tasks.notifyAll();
        }
    }

    public void pushTask(Runnable task, long milliSecondsToWait) {
        synchronized (tasks) {
            if (shutdown) {
                throw new RejectedExecutionException("Tasks can't be scheduled since queue has been shut down.");
            }

            tasks.add(new Entry(task, timer.milliTime() + milliSecondsToWait, sequenceCounter++));
            tasks.notifyAll();
        }
    }

    public boolean isWaiting() {
        synchronized (tasks) {
            return waiting;
        }
    }

    /**
     * Waits until the queue has a task that is ready for scheduling, removes that
     * task from the queue and returns it.
     *
     * @return The next task.
     */
    public Runnable getNextTask() {
        try {
            while (true) {
                synchronized (tasks) {
                    Iterator<Entry> iter = tasks.iterator();
                    if (!iter.hasNext()) {
                        if (shutdown) {
                            return null;
                        }
                        // Set flag for unit tests to coordinate with.
                        waiting = true;
                        tasks.wait();
                        waiting = false;
                        continue;
                    }
                    Entry retEntry = iter.next();
                    long timeNow = timer.milliTime();
                    if (retEntry.getTimestamp() > timeNow) {
                        waiting = true;
                        tasks.wait(retEntry.getTimestamp() - timeNow);
                        waiting = false;
                        continue;
                    }
                    iter.remove();
                    return retEntry.getTask();
                }
            }
        } catch (InterruptedException e) {
            return null;
        }
    }

    /**
     * If there is a task ready for scheduling, remove it from the queue and return it.
     *
     * @return The next task.
     */
    public Runnable popTask() {
        synchronized (tasks) {
            Iterator<Entry> iter = tasks.iterator();
            if (!iter.hasNext()) {
                return null;
            }
            Entry retEntry = iter.next();
            if (retEntry.getTimestamp() > timer.milliTime()) {
                return null;
            }
            iter.remove();
            return retEntry.getTask();
        }
    }

    /** For unit testing only */
    public void wakeTasks() {
        synchronized (tasks) {
            tasks.notifyAll();
        }
    }

    public void shutdown() {
        synchronized (tasks) {
            shutdown = true;
            tasks.notifyAll();
        }
    }

    public boolean isShutdown() {
        synchronized (tasks) {
            return shutdown;
        }
    }

    public long size() {
        synchronized (tasks) {
            return tasks.size();
        }
    }
}
