// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


/**
 * A queue implementation that is not thread-safe. The implementation
 * uses a growable circular array to hold the elements.
 **/
class Queue
{
    private Object[] buf;
    private int      used;
    private int      readPos;
    private int      writePos;

    /**
     * Ensure the queue has room for the specified number of
     * additional elements.
     *
     * @param need space needed on queue
     **/
    private void ensureFree(int need) {
        if (buf.length < used + need) {
            int newSize = Math.max(buf.length, 8);
            while (newSize < used + need) {
                newSize *= 2;
            }
            Object[] newBuf = new Object[newSize];
            for (int i = 0; i < used; i++) {
                newBuf[i] = buf[readPos++];
                if (readPos == buf.length) {
                    readPos = 0;
                }
            }
            buf = newBuf;
            readPos = 0;
            writePos = used; // this cannot wrap
        }
    }

    /**
     * Create a queue. If more elements are put on the queue than can
     * be held by the initial capacity, the underlying structures will
     * be grown as needed.
     *
     * @param capacity initial queue capacity
     **/
    public Queue(int capacity) {
        buf = new Object[capacity];
        used = 0;
        readPos = 0;
        writePos = 0;
    }

    /**
     * Create a queue with an initial capacity of 64.
     **/
    public Queue() {
        this(64);
    }

    /**
     * Enqueue an object on this queue.
     *
     * @param obj the object to enqueue
     **/
    public void enqueue(Object obj) {
        ensureFree(1);
        buf[writePos++] = obj;
        if (writePos == buf.length) {
            writePos = 0;
        }
        used++;
    }

    /**
     * Dequeue the next object from this queue.
     *
     * @return the next object from the queue or 'null' if the queue
     * is empty
     **/
    public Object dequeue() {
        if (used == 0) {
            return null;
        }
        Object obj = buf[readPos];
        buf[readPos++] = null; // enable GC of dequeued object
        if (readPos == buf.length) {
            readPos = 0;
        }
        used--;
        return obj;
    }

    /**
     * @return whether this queue is empty
     **/
    public boolean isEmpty() {
        return (used == 0);
    }

    /**
     * @return the number of elements in this queue
     **/
    public int size() {
        return used;
    }

    /**
     * Flush all elements currently in this queue into another
     * queue. Note that this will clear the queue.
     *
     * @return the number of elements flushed
     **/
    public int flush(Queue dst) {
        int cnt = used;
        dst.ensureFree(cnt);
        for (int i = 0; i < used; i++) {
            dst.buf[dst.writePos++] = buf[readPos];
            buf[readPos++] = null; // enable GC of dequeued object
            if (dst.writePos == dst.buf.length) {
                dst.writePos = 0;
            }
            if (readPos == buf.length) {
                readPos = 0;
            }
        }
        dst.used += used;
        used = 0;
        return cnt;
    }
}
