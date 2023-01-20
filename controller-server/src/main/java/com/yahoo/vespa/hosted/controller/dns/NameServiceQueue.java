// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.dns;

import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.yolean.Exceptions;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.function.Predicate.not;

/**
 * A queue of outstanding {@link NameServiceRequest}s. Requests in this have not yet been dispatched to a
 * {@link NameService} and are thus not visible in DNS.
 *
 * This is immutable.
 *
 * @author mpolden
 * @author jonmv
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

        LinkedList<NameServiceRequest> pending = new LinkedList<>(requests);
        while (n-- > 0 && ! pending.isEmpty()) {
            NameServiceRequest request = pending.poll();
            try {
                request.dispatchTo(nameService);
            }
            catch (Exception e) {
                log.log(Level.WARNING, "Failed to execute " + request + ": " + Exceptions.toMessageString(e) +
                                       ", request will be moved backwards, and retried");

                // Move all requests with the same owner backwards as far as we can, i.e., to the back, or to the first owner-less request.
                Optional<TenantAndApplicationId> owner = request.owner();
                LinkedList<NameServiceRequest> owned = new LinkedList<>();
                LinkedList<NameServiceRequest> others = new LinkedList<>();
                do {
                    if (request.owner().isEmpty()) {
                        pending.push(request);
                        break;  // Can't modify anything past this, as owner-less requests must come in order with all others.
                    }
                    (request.owner().equals(owner) ? owned : others).offer(request);
                }
                while ((request = pending.poll()) != null);
                pending.addAll(0, owned);   // Append owned requests before those we can't modify (or none), and
                pending.addAll(0, others);  // then append requests owned by others before that again.
            }
        }
        return new NameServiceQueue(pending);
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

    /** Returns the queue of records present in this, but not in {@code remaining}. */
    public NameServiceQueue minus(NameServiceQueue remaining) {
        return new NameServiceQueue(requests.stream()
                                            .filter(not(new LinkedList<>(remaining.requests())::remove)) // Count duplicates.
                                            .toList());
    }

    /** Replaces the sublist {@code initial} contained in this with {@code remaining}, or best effort amendment when not contained. */
    public NameServiceQueue replace(NameServiceQueue initial, NameServiceQueue remaining) {
        int sublistIndex = indexOf(requests, initial.requests);
        if (sublistIndex >= 0) {
            LinkedList<NameServiceRequest> updated = new LinkedList<>();
            updated.addAll(requests.subList(0, sublistIndex));
            updated.addAll(remaining.requests);
            updated.addAll(requests.subList(sublistIndex + initial.requests.size(), requests.size()));
            return new NameServiceQueue(updated);
        }
        else {
            log.log(Level.WARNING, "Name service queue has changed unexpectedly; expected requests: " +
                                   initial.requests + " to be present, but that was not found in: " + requests);
            // Do a best-effort amendment, where requests removed from initial to remaining, are removed, from the front, from this.
            return minus(initial.minus(remaining));
        }
    }

    /** Lowest index {@code i} in {@code list} s.t. {@code list.sublist(i, i + sub.size()).equals(sub)}. Naïve implementation. */
    static <T> int indexOf(List<T> list, List<T> sub) {
        for (int i = 0; i + sub.size() <= list.size(); i++)
            if (list.subList(i, i + sub.size()).equals(sub))
                return i;

        return -1;
    }

}
