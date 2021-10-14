// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.core.Document;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Shared document queue that gives clients operations on documents which do not have operations already in flight.
 * This is multithread safe.
 *
 * @author dybis
 */
class DocumentQueue {

    private final Deque<Document> queue;
    private final int maxSize;
    private boolean closed = false;
    private final Clock clock;

    DocumentQueue(int maxSize, Clock clock) {
        this.maxSize = maxSize;
        this.queue = new ArrayDeque<>(maxSize);
        this.clock = clock;
    }

    List<Document> removeAllDocuments() {
        synchronized (queue) {
            List<Document> allDocs = new ArrayList<>();
            while (!queue.isEmpty()) {
                allDocs.add(queue.poll());
            }
            queue.notifyAll();
            return allDocs;
        }
    }

    void put(Document document, boolean calledFromIoThreadGroup) throws InterruptedException {
        document.setQueueInsertTime(clock.instant());
        synchronized (queue) {
            while (!closed && (queue.size() >= maxSize) && !calledFromIoThreadGroup) {
                queue.wait();
            }
            if (closed) {
                throw new IllegalStateException("Cannot add elements to closed queue.");
            }
            queue.add(document);
            queue.notifyAll();
        }
    }

    Document poll(long timeout, TimeUnit unit) throws InterruptedException {
        synchronized (queue) {
            long remainingToWait = unit.toMillis(timeout);
            while (queue.isEmpty()) {
                long startTime = clock.millis();
                queue.wait(remainingToWait);
                remainingToWait -= (clock.millis() - startTime);
                if (remainingToWait <= 0) {
                    break;
                }
            }
            Document document = queue.poll();
            queue.notifyAll();
            return document;
        }
    }

    Document poll() {
        synchronized (queue) {
            Document document = queue.poll();
            queue.notifyAll();
            return document;
        }
    }

    boolean isEmpty() {
        synchronized (queue) {
            return queue.isEmpty();
        }
    }

    int size() {
        synchronized (queue) {
            return queue.size();
        }
    }

    void clear() {
        synchronized (queue) {
            queue.clear();
            queue.notifyAll();
        }
    }

    boolean close() {
        boolean previousState;
        synchronized (queue) {
            previousState = closed;
            closed = true;
            queue.notifyAll();
        }
        return previousState;
    }

    Optional<Document> pollDocumentIfTimedoutInQueue(Duration localQueueTimeOut) {
        synchronized (queue) {
            if (queue.isEmpty()) return Optional.empty();

            Document document = queue.peek();
            if (document.getQueueInsertTime().plus(localQueueTimeOut).isBefore(clock.instant()))
                return Optional.ofNullable(queue.poll());
            else
                return Optional.empty();
        }
    }

}
