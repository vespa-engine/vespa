// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


/**
 * A thread-safe queue wrapper used to pass objects between threads.
 **/
class ThreadQueue
{
    private Queue   queue  = new Queue();
    private boolean closed = false;

    public ThreadQueue() {}

    /**
     * Enqueue an object on this queue. If the queue has been closed,
     * the object will not be queued, and this method will return
     * false.
     *
     * @return true if the object was enqueued, false if this queue
     * was closed
     * @param obj the object to enqueue
     **/
    public boolean enqueue(Object obj) {
        return enqueue(obj, Integer.MAX_VALUE);
    }

    /**
     * Enqueue an object on this queue. If the queue has been closed or
     * the queue already contains too many items, the object will not be
     * queued, and this method will return false.
     *
     * @return true if the object was enqueued, false if this queue
     * was closed or too large
     * @param obj the object to enqueue
     * @param limit more elements than this means the queue is too large
     **/
    public synchronized boolean enqueue(Object obj, int limit) {
        if (closed || (queue.size() > limit)) {
            return false;
        }
        queue.enqueue(obj);
        if (queue.size() == 1) {
            notify(); // assume only one reader
        }
        return true;
    }

    /**
     * Close this queue. After this method is invoked, no more objects
     * may be enqueued on this queue. Also, trying to dequeue an
     * object form a queue that is both empty and closed will cause a
     * {@link EndOfQueueException}.
     **/
    public synchronized void close() {
        closed = true;
        notify();
    }

    /**
     * Dequeue the next object from this queue. This method will block
     * until at least one object is available in the queue.
     *
     * @return the next object from the queue
     * @throws EndOfQueueException if the queue is both empty and
     * closed
     **/
    public synchronized Object dequeue() throws EndOfQueueException {
        while (queue.isEmpty() && !closed) {
            try { wait(); } catch (InterruptedException x) {}
        }
        if (queue.isEmpty()) {
            throw new EndOfQueueException();
        }
        return queue.dequeue();
    }
}
