// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.lang;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Supplier that caches the value for a given duration with ability to invalidate on demand.
 * Is thread safe.
 *
 * @author freva
 */
public class CachedSupplier<T> implements Supplier<T> {

    private final Object monitor = new Object();

    private final Supplier<T> delegate;
    private final Duration period;
    private final Clock clock;

    private Instant nextRefresh;
    private volatile T value;

    public CachedSupplier(Supplier<T> delegate, Duration period) {
        this(delegate, period, Clock.systemUTC());
    }

    CachedSupplier(Supplier<T> delegate, Duration period, Clock clock) {
        this.delegate = delegate;
        this.period = period;
        this.clock = clock;
        this.nextRefresh = Instant.MIN;
    }

    @Override
    public T get() {
        synchronized (monitor) {
            if (clock.instant().isAfter(nextRefresh)) {
                this.value = delegate.get();
                this.nextRefresh = clock.instant().plus(period);
            }
        }

        return value;
    }

    public void invalidate() {
        synchronized (monitor) {
            this.nextRefresh = Instant.MIN;
        }
    }

}
