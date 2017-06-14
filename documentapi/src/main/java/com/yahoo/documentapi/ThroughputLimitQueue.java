// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.concurrent.SystemTimer;
import com.yahoo.concurrent.Timer;

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Queue that limits it's size based on the throughput. Allows the queue to keep a certain number of
 * seconds of processing in its queue.
 */
public class ThroughputLimitQueue<M> extends LinkedBlockingQueue<M> {
    private static Logger log = Logger.getLogger(ThroughputLimitQueue.class.getName());

    double averageWaitTime = 0;
    long maxWaitTime = 0;
    long startTime;
    int capacity = 2;
    Timer timer;

    /**
     * Creates a new queue.
     *
     * @param queueSizeInMs The maximum time we wish to have objects waiting in the queue.
     */
    public ThroughputLimitQueue(long queueSizeInMs) {
        this(SystemTimer.INSTANCE, queueSizeInMs);
    }

    /**
     * Creates a new queue. Used for unit testing.
     *
     * @param t Used to measure time spent in the queue. Subclass for unit testing, or use SystemTimer for regular use.
     * @param queueSizeInMs The maximum time we wish to have objects waiting in the queue.
     */
    public ThroughputLimitQueue(Timer t, long queueSizeInMs) {
        maxWaitTime = queueSizeInMs;
        timer = t;
    }

    // Doc inherited from BlockingQueue
    public boolean add(M m) {
        if (!offer(m)) {
            throw new IllegalStateException("Queue full");
        }
        return true;
    }

    // Doc inherited from BlockingQueue
    public boolean offer(M m) {
        return remainingCapacity() > 0 && super.offer(m);
    }

    /**
     * Calculates the average waiting time and readjusts the queue capacity.
     *
     * @param m The last message that was read from queue, if any.
     * @return Returns m.
     */
    private M calculateAverage(M m) {
        if (m == null) {
            startTime = 0;
            return null;
        }

        if (startTime != 0) {
            long waitTime = timer.milliTime() - startTime;

            if (averageWaitTime == 0) {
                averageWaitTime = waitTime;
            } else {
                averageWaitTime = 0.99 * averageWaitTime + 0.01 * waitTime;
            }

            int newCapacity = Math.max(2, (int)Math.round(maxWaitTime / averageWaitTime));
            if (newCapacity != capacity) {
                log.fine("Capacity of throughput queue changed from " + capacity + " to " + newCapacity);
                capacity = newCapacity;
            }
        }

        if (!isEmpty()) {
            startTime = timer.milliTime();
        } else {
            startTime = 0;
        }

        return m;
    }

    // Doc inherited from BlockingQueue
    public M poll() {
        return calculateAverage(super.poll());
    }

    // Doc inherited from BlockingQueue
    public void put(M m) throws InterruptedException {
        offer(m, Long.MAX_VALUE, TimeUnit.SECONDS);
    }

    // Doc inherited from BlockingQueue
    public boolean offer(M m, long l, TimeUnit timeUnit) throws InterruptedException {
        long timeWaited = 0;
        while (timeWaited < timeUnit.toMillis(l)) {
            if (offer(m)) {
                return true;
            }

            Thread.sleep(10);
            timeWaited += 10;
        }

        return false;
    }

    // Doc inherited from BlockingQueue
    public M take() throws InterruptedException {
        return poll(Long.MAX_VALUE, TimeUnit.SECONDS);
    }

    // Doc inherited from BlockingQueue
    public M poll(long l, TimeUnit timeUnit) throws InterruptedException {
        long timeWaited = 0;
        while (timeWaited < timeUnit.toMillis(l)) {
            M elem = poll();
            if (elem != null) {
                return elem;
            }

            Thread.sleep(10);
            timeWaited += 10;
        }

        return null;
    }

    /**
     * @return Returns the maximum capacity of the queue
     */
    public int capacity() {
        return capacity;
    }

    // Doc inherited from BlockingQueue
    public int remainingCapacity() {
        int sz = capacity - size();
        return (sz > 0) ? sz : 0;
    }

    // Doc inherited from BlockingQueue
    public boolean addAll(Collection<? extends M> ms) {
        for (M m : ms) {
            if (!offer(m)) {
                return false;
            }
        }

        return true;
    }
}
