// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.security.tls;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author bjorncs
 */
public class TlsMetrics {
    private static final TlsMetrics instance = new TlsMetrics();

    private final AtomicLong capabilityChecksSucceeded = new AtomicLong(0);
    private final AtomicLong capabilityChecksFailed = new AtomicLong(0);

    private TlsMetrics() {}

    public static TlsMetrics instance() { return instance; }

    void incrementCapabilitiesSucceeded() { capabilityChecksSucceeded.incrementAndGet(); }
    void incrementCapabilitiesFailed() { capabilityChecksFailed.incrementAndGet(); }
    public Snapshot snapshot() { return new Snapshot(this); }

    public record Snapshot(long capabilityChecksSucceeded, long capabilityChecksFailed) {
        public static final Snapshot EMPTY = new Snapshot(0, 0);
        private Snapshot(TlsMetrics m) { this(m.capabilityChecksSucceeded.get(), m.capabilityChecksFailed.get()); }
        public Diff changesSince(Snapshot previous) { return new Diff(this, previous); }
    }

    public record Diff(long capabilityChecksSucceeded, long capabilityChecksFailed) {
        private Diff(Snapshot current, Snapshot previous) {
            this(current.capabilityChecksSucceeded - previous.capabilityChecksSucceeded,
                 current.capabilityChecksFailed - previous.capabilityChecksFailed);
        }
    }
}
