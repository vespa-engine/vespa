// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.util.concurrent.atomic.AtomicLong;

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

    void reset() {
        tlsCertificateVerificationFailures.set(0);
        peerAuthorizationFailures.set(0);
        serverTlsConnectionsEstablished.set(0);
        clientTlsConnectionsEstablished.set(0);
        serverUnencryptedConnectionsEstablished.set(0);
        clientUnencryptedConnectionsEstablished.set(0);
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
        private final long tlsCertificateVerificationFailures, peerAuthorizationFailures, serverTlsConnectionsEstablished,
                clientTlsConnectionsEstablished, serverUnencryptedConnectionsEstablished, clientUnencryptedConnectionsEstablished;

        private Snapshot(TransportMetrics metrics) {
            tlsCertificateVerificationFailures = metrics.tlsCertificateVerificationFailures.get();
            peerAuthorizationFailures = metrics.peerAuthorizationFailures.get();
            serverTlsConnectionsEstablished = metrics.serverTlsConnectionsEstablished.get();
            clientTlsConnectionsEstablished = metrics.clientTlsConnectionsEstablished.get();
            serverUnencryptedConnectionsEstablished = metrics.serverUnencryptedConnectionsEstablished.get();
            clientUnencryptedConnectionsEstablished = metrics.clientUnencryptedConnectionsEstablished.get();
        }

        public long tlsCertificateVerificationFailures() { return tlsCertificateVerificationFailures; }
        public long peerAuthorizationFailures() { return peerAuthorizationFailures; }
        public long serverTlsConnectionsEstablished() { return serverTlsConnectionsEstablished; }
        public long clientTlsConnectionsEstablished() { return clientTlsConnectionsEstablished; }
        public long serverUnencryptedConnectionsEstablished() { return serverUnencryptedConnectionsEstablished; }
        public long clientUnencryptedConnectionsEstablished() { return clientUnencryptedConnectionsEstablished; }

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
