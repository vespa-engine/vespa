// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.collections.LazyMap;
import com.yahoo.language.Language;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Context of an invocation of a component carrying out a processing task.
 *
 * @author bratseth
 */
public class InvocationContext<SUBCLASS extends InvocationContext<SUBCLASS>> {

    public enum DestinationType {
        /** The embedding is for a query feature with format "query(feature)". */
        QUERY,
        /** The embedding is for a document field with format "schema.field". */
        DOCUMENT;

        public static DestinationType fromString(String destination) {
            return destination.startsWith("query(") ? QUERY : DOCUMENT;
        }
    }

    private Language language = Language.UNKNOWN;
    private String destination;
    private String componentId = "unknown";
    private final Map<Object, Object> cache;
    private Deadline deadline;

    public InvocationContext(String destination) {
        this(destination, LazyMap.newHashMap());
    }

    /**
     * @param destination the name of the recipient of this invocation
     * @param cache       a cache shared between all invocations for a single request
     */
    public InvocationContext(String destination, Map<Object, Object> cache) {
        this.destination = destination;
        this.cache = Objects.requireNonNull(cache);
    }

    protected InvocationContext(SUBCLASS other) {
        language = other.getLanguage();
        destination = other.getDestination();
        componentId = other.getComponentId();
        deadline = other.getDeadline().orElse(null);
        this.cache = other.getCache();
    }

    /** Returns the language of the text, or UNKNOWN (default) to use a language independent invocation. */
    public Language getLanguage() {return language;}

    /** Sets the language of the text, or UNKNOWN to use language a independent invocation. */
    @SuppressWarnings("unchecked")
    public SUBCLASS setLanguage(Language language) {
        this.language = language != null ? language : Language.UNKNOWN;
        return (SUBCLASS)this;
    }

    /** Returns the name of the recipient of this invocation. See {@link DestinationType} for format details. */
    public String getDestination() { return destination; }

    /** Returns the type of destination */
    public DestinationType getDestinationType() { return DestinationType.fromString(destination); }

    /** Sets the name of the recipient of this invocation. See {@link DestinationType} for format details. */
    @SuppressWarnings("unchecked")
    public SUBCLASS setDestination(String destination) {
        this.destination = destination;
        return (SUBCLASS)this;
    }

    /** @return the operation timeout represented as a {@link Deadline} */
    public Optional<Deadline> getDeadline() { return Optional.ofNullable(deadline); }

    @SuppressWarnings("unchecked")
    public SUBCLASS setDeadline(Deadline deadline) { this.deadline = deadline; return (SUBCLASS)this; }

    /** Return the component id or 'unknown' if not set. */
    public String getComponentId() { return componentId; }

    /** Sets the component id. */
    @SuppressWarnings("unchecked")
    public SUBCLASS setComponentId(String componentId) {
        this.componentId = componentId;
        return (SUBCLASS)this;
    }

    protected Map<Object, Object> getCache() { return cache; }

    public void putCachedValue(Object key, Object value) {
        cache.put(key, value);
    }

    /** Returns a cached value, or null if not present. */
    public Object getCachedValue(Object key) {
        return cache.get(key);
    }

    /** Returns the cached value, or computes and caches it if not present. */
    @SuppressWarnings("unchecked")
    public <T> T computeCachedValueIfAbsent(Object key, Supplier<? extends T> supplier) {
        return (T) cache.computeIfAbsent(key, __ -> supplier.get());
    }

    public static class Deadline {
        private final Clock clock;
        private final Instant deadlineInstant;

        private Deadline(Clock clock, Instant deadlineInstant) {
            this.clock = clock;
            this.deadlineInstant = deadlineInstant;
        }

        public static Deadline of(Duration duration) {
            return new Deadline(Clock.systemUTC(), Clock.systemUTC().instant().plus(duration));
        }

        public static Deadline of(Instant deadline) {
            return new Deadline(Clock.systemUTC(), deadline);
        }

        public boolean isExpired() { return clock.instant().isAfter(deadlineInstant); }

        public Duration timeRemaining() {
            Instant now = clock.instant();
            if (now.isAfter(deadlineInstant)) {
                return Duration.ZERO;
            }
            return Duration.between(now, deadlineInstant);
        }
        public Instant asInstant() { return deadlineInstant; }
    }

}
