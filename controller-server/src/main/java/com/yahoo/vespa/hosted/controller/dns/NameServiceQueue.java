// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.dns;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Logger;

/**
 * A queue of outstanding {@link NameServiceRequest}s. Requests in this have not yet been dispatched to a
 * {@link NameService} and are thus not visible in DNS.
 *
 * This is immutable.
 *
 * @author mpolden
 */
public class NameServiceQueue {

    public static final NameServiceQueue EMPTY = new NameServiceQueue(List.of());

    private static final Logger log = Logger.getLogger(NameServiceQueue.class.getName());

    private final LinkedBlockingDeque<NameServiceRequest> requests;

    /** DO NOT USE. Public for serialization purposes */
    public NameServiceQueue(Collection<NameServiceRequest> requests) {
        this.requests = new LinkedBlockingDeque<>();
        this.requests.addAll(Objects.requireNonNull(requests, "requests must be non-null"));
    }

    /** Returns a view of requests in this queue */
    public Collection<NameServiceRequest> requests() {
        return Collections.unmodifiableCollection(requests);
    }

    /** Returns a copy of this containing only the n most recent requests */
    public NameServiceQueue last(int n) {
        requireNonNegative(n);
        if (requests.size() <= n) return this;
        List<NameServiceRequest> requests = new ArrayList<>(this.requests);
        return new NameServiceQueue(requests.subList(requests.size() - n, requests.size()));
    }

    /** Returns a copy of this with given request queued according to priority */
    public NameServiceQueue with(NameServiceRequest request, Priority priority) {
        var queue = new NameServiceQueue(this.requests);
        if (priority == Priority.high) {
            queue.requests.addFirst(request);
        } else {
            queue.requests.add(request);
        }
        return queue;
    }

    public NameServiceQueue with(NameServiceRequest request) {
        return with(request, Priority.normal);
    }

    /**
     * Dispatch n requests from the head of this to given name service.
     *
     * @return A copy of this, without the successfully dispatched requests.
     */
    public NameServiceQueue dispatchTo(NameService nameService, int n) {
        requireNonNegative(n);
        if (requests.isEmpty()) return this;

        var queue = new NameServiceQueue(requests);
        for (var i = 0; i < n && !queue.requests.isEmpty(); i++) {
            var request = queue.requests.peek();
            try {
                request.dispatchTo(nameService);
                queue.requests.poll();
            } catch (Exception e) {
                log.log(LogLevel.WARNING, "Failed to execute " + request + ": " + e.getMessage() +
                                          ", request will be retried");
            }
        }

        return queue;
    }

    @Override
    public String toString() {
        return requests.toString();
    }

    private static void requireNonNegative(int n) {
        if (n < 0) throw new IllegalArgumentException("n must be >= 0, got " + n);
    }

    /** Priority of a request added to this */
    public enum Priority {
        /** Default priority. Request will be delivered in FIFO order */
        normal,

        /**Request is queued before others. Useful for code that needs to act on effects of a request */
        high
    }

}
