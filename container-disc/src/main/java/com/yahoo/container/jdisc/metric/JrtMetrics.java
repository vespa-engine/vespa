// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

// import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Metric;
import com.yahoo.jrt.TransportMetrics;
import com.yahoo.metrics.ContainerMetrics;

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
        increment(ContainerMetrics.JRT_TRANSPORT_TLS_CERTIFICATE_VERIFICATION_FAILURES.baseName(), changesSincePrevious.tlsCertificateVerificationFailures());
        increment(ContainerMetrics.JRT_TRANSPORT_PEER_AUTHORIZATION_FAILURES.baseName(), changesSincePrevious.peerAuthorizationFailures());
        increment(ContainerMetrics.JRT_TRANSPORT_SERVER_TLS_CONNECIONTS_ESTABLISHED.baseName(), changesSincePrevious.serverTlsConnectionsEstablished());
        increment(ContainerMetrics.JRT_TRANSPORT_CLIENT_TLS_CONNECTIONS_ESTABLISHED.baseName(), changesSincePrevious.clientTlsConnectionsEstablished());
        increment(ContainerMetrics.JRT_TRANSPORT_CLIENT_UNENCRYPTED_CONNECTIONS_ESTABLISHED.baseName(), changesSincePrevious.serverUnencryptedConnectionsEstablished());
        increment(ContainerMetrics.JRT_TRANSPORT_CLIENT_UNENCRYPTED_CONNECTIONS_ESTABLISHED.baseName(), changesSincePrevious.clientUnencryptedConnectionsEstablished());
        previousSnapshot = snapshot;
    }

    private void increment(String metricName, long countIncrement) {
        if (countIncrement > 0) {
            metric.add(metricName, countIncrement, null);
        }
    }

}
