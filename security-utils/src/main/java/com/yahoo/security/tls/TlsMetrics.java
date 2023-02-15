// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.security.tls;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author bjorncs
 */
public class TlsMetrics {
    private static final TlsMetrics instance = new TlsMetrics();

    private final AtomicLong capabilitiesSucceeded = new AtomicLong(0);
    private final AtomicLong capabilitiesFailed = new AtomicLong(0);

    private TlsMetrics() {}

    public static TlsMetrics instance() { return instance; }

    void incrementCapabilitiesSucceeded() { capabilitiesSucceeded.incrementAndGet(); }
    void incrementCapabilitiesFailed() { capabilitiesFailed.incrementAndGet(); }
    public Snapshot snapshot() { return new Snapshot(this); }

    public record Snapshot(long capabilitiesSucceeded, long capabilitiesFailed) {
        public static final Snapshot EMPTY = new Snapshot(0, 0);
        private Snapshot(TlsMetrics m) { this(m.capabilitiesSucceeded.get(), m.capabilitiesFailed.get()); }
        public Diff changesSince(Snapshot previous) { return new Diff(this, previous); }
    }

    public record Diff(long capabilitiesSucceeded, long capabilitiesFailed) {
        private Diff(Snapshot current, Snapshot previous) {
            this(current.capabilitiesSucceeded - previous.capabilitiesSucceeded,
                 current.capabilitiesFailed - previous.capabilitiesFailed);
        }
    }
}
