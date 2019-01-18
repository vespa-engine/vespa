// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.yahoo.jdisc.Metric;
import com.yahoo.jrt.TransportMetrics;

import java.util.function.ToLongFunction;

import static com.yahoo.jrt.TransportMetrics.*;

/**
 * Emits jrt metrics
 *
 * @author bjorncs
 */
class JrtMetrics {

    private final TransportMetrics transportMetrics = TransportMetrics.getInstance();
    private final Metric metric;
    private volatile Snapshot previousSnapshot;

    JrtMetrics(Metric metric) {
        this.metric = metric;
    }

    void emitMetrics() {
        Snapshot snapshot = transportMetrics.snapshot();
        increment("jrt.transport.tls-certificate-verification-failures", Snapshot::tlsCertificateVerificationFailures, snapshot);
        increment("jrt.transport.peer-authorization-failures", Snapshot::peerAuthorizationFailures, snapshot);
        increment("jrt.transport.server.tls-connections-established", Snapshot::serverTlsConnectionsEstablished, snapshot);
        increment("jrt.transport.client.tls-connections-established", Snapshot::clientTlsConnectionsEstablished, snapshot);
        increment("jrt.transport.server.unencrypted-connections-established", Snapshot::serverUnencryptedConnectionsEstablished, snapshot);
        increment("jrt.transport.client.unencrypted-connections-established", Snapshot::clientUnencryptedConnectionsEstablished, snapshot);
        previousSnapshot = snapshot;
    }

    private void increment(String metricName, ToLongFunction<Snapshot> metricGetter, Snapshot snapshot) {
        long currentValue = metricGetter.applyAsLong(snapshot);
        long increment = previousSnapshot != null
                ? currentValue - metricGetter.applyAsLong(previousSnapshot)
                : currentValue;
        metric.add(metricName, increment, null);
    }
}
