// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import org.eclipse.jetty.io.ssl.SslHandshakeListener;

import javax.net.ssl.SSLHandshakeException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * A {@link SslHandshakeListener} that reports metrics for SSL handshake failures.
 *
 * @author bjorncs
 */
class SslHandshakeFailedListener implements SslHandshakeListener {

    private final static Logger log = Logger.getLogger(SslHandshakeFailedListener.class.getName());

    private final Metric metric;
    private final String connectorName;
    private final int listenPort;

    SslHandshakeFailedListener(Metric metric, String connectorName, int listenPort) {
        this.metric = metric;
        this.connectorName = connectorName;
        this.listenPort = listenPort;
    }

    @Override
    public void handshakeFailed(Event event, Throwable throwable) {
        log.log(Level.FINE, throwable, () -> "Ssl handshake failed: " + throwable.getMessage());
        String metricName = SslHandshakeFailure.fromSslHandshakeException((SSLHandshakeException) throwable)
                .map(SslHandshakeFailure::metricName)
                .orElse(MetricDefinitions.SSL_HANDSHAKE_FAILURE_UNKNOWN);
        metric.add(metricName, 1L, metric.createContext(createDimensions(event)));
    }

    private Map<String, Object> createDimensions(Event event) {
        Map<String, Object> dimensions = new HashMap<>();
        dimensions.put(MetricDefinitions.NAME_DIMENSION, connectorName);
        dimensions.put(MetricDefinitions.PORT_DIMENSION, listenPort);
        Optional.ofNullable(event.getSSLEngine().getPeerHost())
                .ifPresent(clientIp -> dimensions.put(MetricDefinitions.CLIENT_IP_DIMENSION, clientIp));
        return Map.copyOf(dimensions);
    }
}
