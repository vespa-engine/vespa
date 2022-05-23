// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import org.eclipse.jetty.io.EofException;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Categorizes instances of {@link SSLHandshakeException}
 *
 * @author bjorncs
 */
enum SslHandshakeFailure {
    INCOMPATIBLE_PROTOCOLS(
            MetricDefinitions.SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_PROTOCOLS,
            "INCOMPATIBLE_CLIENT_PROTOCOLS",
            "(Client requested protocol \\S+? is not enabled or supported in server context" +
                    "|The client supported protocol versions \\[.+?\\] are not accepted by server preferences \\[.+?\\])"),
    INCOMPATIBLE_CIPHERS(
            MetricDefinitions.SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_CIPHERS,
            "INCOMPATIBLE_CLIENT_CIPHER_SUITES",
            "no cipher suites in common"),
    MISSING_CLIENT_CERT(
            MetricDefinitions.SSL_HANDSHAKE_FAILURE_MISSING_CLIENT_CERT,
            "MISSING_CLIENT_CERTIFICATE",
            "Empty (server|client) certificate chain"),
    EXPIRED_CLIENT_CERTIFICATE(
            MetricDefinitions.SSL_HANDSHAKE_FAILURE_EXPIRED_CLIENT_CERT,
            "EXPIRED_CLIENT_CERTIFICATE",
            // Note: this pattern will match certificates with too late notBefore as well
            "PKIX path validation failed: java.security.cert.CertPathValidatorException: validity check failed"),
    INVALID_CLIENT_CERT(
            MetricDefinitions.SSL_HANDSHAKE_FAILURE_INVALID_CLIENT_CERT, // Includes mismatch of client certificate and private key
            "INVALID_CLIENT_CERTIFICATE",
            "(PKIX path (building|validation) failed: .+)|(Invalid CertificateVerify signature)"),
    CONNECTION_CLOSED(
            MetricDefinitions.SSL_HANDSHAKE_FAILURE_CONNECTION_CLOSED,
            "CONNECTION_CLOSED",
            e -> e.getCause() instanceof EofException
                    && e.getCause().getCause() instanceof IOException
                    && e.getCause().getCause().getMessage().equals("Broken pipe"));

    private final String metricName;
    private final String failureType;
    private final Predicate<SSLHandshakeException> predicate;

    SslHandshakeFailure(String metricName, String failureType, String messagePattern) {
        this(metricName, failureType, new MessagePatternPredicate(messagePattern));
    }

    SslHandshakeFailure(String metricName, String failureType, Predicate<SSLHandshakeException> predicate) {
        this.metricName = metricName;
        this.failureType = failureType;
        this.predicate = predicate;
    }

    String metricName() { return metricName; }
    String failureType() { return failureType; }

    static Optional<SslHandshakeFailure> fromSslHandshakeException(SSLHandshakeException exception) {
        for (SslHandshakeFailure type : values()) {
            if (type.predicate.test(exception)) return Optional.of(type);
        }
        return Optional.empty();
    }

    private static class MessagePatternPredicate implements Predicate<SSLHandshakeException> {
        final Pattern pattern;

        MessagePatternPredicate(String pattern) { this.pattern = Pattern.compile(pattern); }

        @Override
        public boolean test(SSLHandshakeException e) {
            String message = e.getMessage();
            if (message == null || message.isBlank()) return false;
            return pattern.matcher(message).matches();
        }
    }

}
