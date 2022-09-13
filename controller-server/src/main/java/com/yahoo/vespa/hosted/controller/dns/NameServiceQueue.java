// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.dns;

import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.yolean.Exceptions;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A queue of outstanding {@link NameServiceRequest}s. Requests in this have not yet been dispatched to a
 * {@link NameService} and are thus not visible in DNS.
 *
 * This is immutable.
 *
 * @author mpolden
 */
public record NameServiceQueue(List<NameServiceRequest> requests) {

    public static final NameServiceQueue EMPTY = new NameServiceQueue(List.of());

    private static final Logger log = Logger.getLogger(NameServiceQueue.class.getName());

    /** DO NOT USE. Public for serialization purposes */
    public NameServiceQueue(List<NameServiceRequest> requests) {
        this.requests = List.copyOf(Objects.requireNonNull(requests, "requests must be non-null"));
    }

    /** Returns a copy of this containing the last n requests */
    public NameServiceQueue last(int n) {
        return resize(n, (requests) -> requests.subList(requests.size() - n, requests.size()));
    }

    /** Returns a copy of this containing the first n requests */
    public NameServiceQueue first(int n) {
        return resize(n, (requests) -> requests.subList(0, n));
    }

    /** Returns a copy of this with given request queued according to priority */
    public NameServiceQueue with(NameServiceRequest request, Priority priority) {
        List<NameServiceRequest> copy = new ArrayList<>(this.requests.size() + 1);
        switch (priority) {
            case normal -> {
                copy.addAll(this.requests);
                copy.add(request);
            }
            case high -> {
                copy.add(request);
                copy.addAll(this.requests);
            }
        }
        return new NameServiceQueue(copy);
    }

    /** Returns a copy of this with given request added */
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

        LinkedList<NameServiceRequest> copy = new LinkedList<>(requests);
        for (int i = 0; i < n && !copy.isEmpty(); i++) {
            var request = copy.peek();
            try {
                request.dispatchTo(nameService);
                copy.poll();
            } catch (Exception e) {
                log.log(Level.WARNING, "Failed to execute " + request + ": " + Exceptions.toMessageString(e) +
                                          ", request will be retried");
            }
        }
        return new NameServiceQueue(copy);
    }

    @Override
    public String toString() {
        return requests.toString();
    }

    private NameServiceQueue resize(int n, UnaryOperator<List<NameServiceRequest>> resizer) {
        requireNonNegative(n);
        if (requests.size() <= n) return this;
        return new NameServiceQueue(resizer.apply(requests));
    }

    private static void requireNonNegative(int n) {
        if (n < 0) throw new IllegalArgumentException("n must be >= 0, got " + n);
    }

    /** Priority of a request added to this */
    public enum Priority {

        /** Default priority. Request will be delivered in FIFO order */
        normal,

        /** Request is queued first. Useful for code that needs to act on effects of a request */
        high

    }

}
