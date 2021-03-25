// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import javax.net.ssl.SSLHandshakeException;
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
            "(PKIX path (building|validation) failed: .+)|(Invalid CertificateVerify signature)");

    private final String metricName;
    private final String failureType;
    private final Predicate<String> messageMatcher;

    SslHandshakeFailure(String metricName, String failureType, String messagePattern) {
        this.metricName = metricName;
        this.failureType = failureType;
        this.messageMatcher = Pattern.compile(messagePattern).asMatchPredicate();
    }

    String metricName() { return metricName; }
    String failureType() { return failureType; }

    static Optional<SslHandshakeFailure> fromSslHandshakeException(SSLHandshakeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return Optional.empty();
        for (SslHandshakeFailure failure : values()) {
            if (failure.messageMatcher.test(message)) {
                return Optional.of(failure);
            }
        }
        return Optional.empty();
    }
}
