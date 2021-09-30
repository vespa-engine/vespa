// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.yahoo.jdisc.Metric;
import com.yahoo.jrt.TransportMetrics;

import static com.yahoo.jrt.TransportMetrics.Snapshot;

/**
 * Emits jrt metrics
 *
 * @author bjorncs
 */
class JrtMetrics {

    private final TransportMetrics transportMetrics = TransportMetrics.getInstance();
    private final Metric metric;
    private Snapshot previousSnapshot = Snapshot.EMPTY;

    JrtMetrics(Metric metric) {
        this.metric = metric;
    }

    void emitMetrics() {
        Snapshot snapshot = transportMetrics.snapshot();
        Snapshot changesSincePrevious = snapshot.changesSince(previousSnapshot);
        increment("jrt.transport.tls-certificate-verification-failures", changesSincePrevious.tlsCertificateVerificationFailures());
        increment("jrt.transport.peer-authorization-failures", changesSincePrevious.peerAuthorizationFailures());
        increment("jrt.transport.server.tls-connections-established", changesSincePrevious.serverTlsConnectionsEstablished());
        increment("jrt.transport.client.tls-connections-established", changesSincePrevious.clientTlsConnectionsEstablished());
        increment("jrt.transport.server.unencrypted-connections-established", changesSincePrevious.serverUnencryptedConnectionsEstablished());
        increment("jrt.transport.client.unencrypted-connections-established", changesSincePrevious.clientUnencryptedConnectionsEstablished());
        previousSnapshot = snapshot;
    }

    private void increment(String metricName, long countIncrement) {
        if (countIncrement > 0) {
            metric.add(metricName, countIncrement, null);
        }
    }

}
