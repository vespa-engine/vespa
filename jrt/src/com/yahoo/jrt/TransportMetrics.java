// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToLongFunction;

/**
 * Metric values produced by {@link Transport}.
 *
 * @author bjorncs
 */
public class TransportMetrics {

    private static final TransportMetrics instance = new TransportMetrics();

    private final AtomicLong tlsCertificateVerificationFailures = new AtomicLong(0);
    private final AtomicLong peerAuthorizationFailures = new AtomicLong(0);
    private final AtomicLong serverTlsConnectionsEstablished = new AtomicLong(0);
    private final AtomicLong clientTlsConnectionsEstablished = new AtomicLong(0);
    private final AtomicLong serverUnencryptedConnectionsEstablished = new AtomicLong(0);
    private final AtomicLong clientUnencryptedConnectionsEstablished = new AtomicLong(0);

    private TransportMetrics() {}

    public static TransportMetrics getInstance() { return instance; }

    public long tlsCertificateVerificationFailures() {
        return tlsCertificateVerificationFailures.get();
    }

    public long peerAuthorizationFailures() {
        return peerAuthorizationFailures.get();
    }

    public long serverTlsConnectionsEstablished() {
        return serverTlsConnectionsEstablished.get();
    }

    public long clientTlsConnectionsEstablished() {
        return clientTlsConnectionsEstablished.get();
    }

    public long serverUnencryptedConnectionsEstablished() {
        return serverUnencryptedConnectionsEstablished.get();
    }

    public long clientUnencryptedConnectionsEstablished() {
        return clientUnencryptedConnectionsEstablished.get();
    }

    public Snapshot snapshot() { return new Snapshot(this); }

    void incrementTlsCertificateVerificationFailures() {
        tlsCertificateVerificationFailures.incrementAndGet();
    }

    void incrementPeerAuthorizationFailures() {
        peerAuthorizationFailures.incrementAndGet();
    }

    void incrementServerTlsConnectionsEstablished() {
        serverTlsConnectionsEstablished.incrementAndGet();
    }

    void incrementClientTlsConnectionsEstablished() {
        clientTlsConnectionsEstablished.incrementAndGet();
    }

    void incrementServerUnencryptedConnectionsEstablished() {
        serverUnencryptedConnectionsEstablished.incrementAndGet();
    }

    void incrementClientUnencryptedConnectionsEstablished() {
        clientUnencryptedConnectionsEstablished.incrementAndGet();
    }

    @Override
    public String toString() {
        return "TransportMetrics{" +
                "tlsCertificateVerificationFailures=" + tlsCertificateVerificationFailures +
                ", peerAuthorizationFailures=" + peerAuthorizationFailures +
                ", serverTlsConnectionsEstablished=" + serverTlsConnectionsEstablished +
                ", clientTlsConnectionsEstablished=" + clientTlsConnectionsEstablished +
                ", serverUnencryptedConnectionsEstablished=" + serverUnencryptedConnectionsEstablished +
                ", clientUnencryptedConnectionsEstablished=" + clientUnencryptedConnectionsEstablished +
                '}';
    }

    public static class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, 0);

        private final long tlsCertificateVerificationFailures;
        private final long peerAuthorizationFailures;
        private final long serverTlsConnectionsEstablished;
        private final long clientTlsConnectionsEstablished;
        private final long serverUnencryptedConnectionsEstablished;
        private final long clientUnencryptedConnectionsEstablished;

        private Snapshot(TransportMetrics metrics) {
            this(metrics.tlsCertificateVerificationFailures.get(),
                 metrics.peerAuthorizationFailures.get(),
                 metrics.serverTlsConnectionsEstablished.get(),
                 metrics.clientTlsConnectionsEstablished.get(),
                 metrics.serverUnencryptedConnectionsEstablished.get(),
                 metrics.clientUnencryptedConnectionsEstablished.get());
        }

        private Snapshot(long tlsCertificateVerificationFailures,
                        long peerAuthorizationFailures,
                        long serverTlsConnectionsEstablished,
                        long clientTlsConnectionsEstablished,
                        long serverUnencryptedConnectionsEstablished,
                        long clientUnencryptedConnectionsEstablished) {
            this.tlsCertificateVerificationFailures = tlsCertificateVerificationFailures;
            this.peerAuthorizationFailures = peerAuthorizationFailures;
            this.serverTlsConnectionsEstablished = serverTlsConnectionsEstablished;
            this.clientTlsConnectionsEstablished = clientTlsConnectionsEstablished;
            this.serverUnencryptedConnectionsEstablished = serverUnencryptedConnectionsEstablished;
            this.clientUnencryptedConnectionsEstablished = clientUnencryptedConnectionsEstablished;
        }

        public long tlsCertificateVerificationFailures() { return tlsCertificateVerificationFailures; }
        public long peerAuthorizationFailures() { return peerAuthorizationFailures; }
        public long serverTlsConnectionsEstablished() { return serverTlsConnectionsEstablished; }
        public long clientTlsConnectionsEstablished() { return clientTlsConnectionsEstablished; }
        public long serverUnencryptedConnectionsEstablished() { return serverUnencryptedConnectionsEstablished; }
        public long clientUnencryptedConnectionsEstablished() { return clientUnencryptedConnectionsEstablished; }

        public Snapshot changesSince(Snapshot base) {
            return new Snapshot(
                changesSince(base, Snapshot::tlsCertificateVerificationFailures),
                changesSince(base, Snapshot::peerAuthorizationFailures),
                changesSince(base, Snapshot::serverTlsConnectionsEstablished),
                changesSince(base, Snapshot::clientTlsConnectionsEstablished),
                changesSince(base, Snapshot::serverUnencryptedConnectionsEstablished),
                changesSince(base, Snapshot::clientUnencryptedConnectionsEstablished));
        }

        private long changesSince(Snapshot base, ToLongFunction<Snapshot> metricProperty) {
            return metricProperty.applyAsLong(this) - metricProperty.applyAsLong(base);
        }

        @Override
        public String toString() {
            return "Snapshot{" +
                    "tlsCertificateVerificationFailures=" + tlsCertificateVerificationFailures +
                    ", peerAuthorizationFailures=" + peerAuthorizationFailures +
                    ", serverTlsConnectionsEstablished=" + serverTlsConnectionsEstablished +
                    ", clientTlsConnectionsEstablished=" + clientTlsConnectionsEstablished +
                    ", serverUnencryptedConnectionsEstablished=" + serverUnencryptedConnectionsEstablished +
                    ", clientUnencryptedConnectionsEstablished=" + clientUnencryptedConnectionsEstablished +
                    '}';
        }
    }
}
