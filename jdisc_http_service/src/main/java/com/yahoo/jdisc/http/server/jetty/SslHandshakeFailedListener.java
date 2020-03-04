// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.server.jetty.JettyHttpServer.Metrics;
import org.eclipse.jetty.io.ssl.SslHandshakeListener;

import javax.net.ssl.SSLHandshakeException;
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
                .orElse(Metrics.SSL_HANDSHAKE_FAILURE_UNKNOWN);
        metric.add(metricName, 1L, metric.createContext(createDimensions()));
    }

    private Map<String, Object> createDimensions() {
        return Map.of(Metrics.NAME_DIMENSION, connectorName, Metrics.PORT_DIMENSION, listenPort);
    }

    private enum SslHandshakeFailure {
        INCOMPATIBLE_PROTOCOLS(
                Metrics.SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_PROTOCOLS,
                "(Client requested protocol \\S+? is not enabled or supported in server context" +
                        "|The client supported protocol versions \\[\\S+?\\] are not accepted by server preferences \\[\\S+?\\])"),
        INCOMPATIBLE_CIPHERS(
                Metrics.SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_CIPHERS,
                "no cipher suites in common"),
        MISSING_CLIENT_CERT(
                Metrics.SSL_HANDSHAKE_FAILURE_MISSING_CLIENT_CERT,
                "Empty server certificate chain"),
        INVALID_CLIENT_CERT(
                Metrics.SSL_HANDSHAKE_FAILURE_INVALID_CLIENT_CERT,
                "PKIX path (building|validation) failed: .+");

        private final String metricName;
        private final Predicate<String> messageMatcher;

        SslHandshakeFailure(String metricName, String messagePattern) {
            this.metricName = metricName;
            this.messageMatcher = Pattern.compile(messagePattern).asMatchPredicate();
        }

        String metricName() { return metricName; }

        static Optional<SslHandshakeFailure> fromSslHandshakeException(SSLHandshakeException exception) {
            String message = exception.getMessage();
            for (SslHandshakeFailure failure : values()) {
                if (failure.messageMatcher.test(message)) {
                    return Optional.of(failure);
                }
            }
            return Optional.empty();
        }
    }
}
