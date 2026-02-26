// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import ai.vespa.utils.BytesQuantity;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.text.Text;
import com.yahoo.vespa.model.container.http.ConnectorFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Component specification for {@link com.yahoo.jdisc.http.server.jetty.ConnectorFactory} with hosted specific configuration.
 *
 * @author bjorncs
 */
public class HostedSslConnectorFactory extends ConnectorFactory {

    private record EntityLoggingEntry(String prefix, double sampleRate, BytesQuantity maxEntitySize) {}

    private final SslClientAuth clientAuth;
    private final List<String> tlsCiphersOverride;
    private final boolean proxyProtocolEnabled;
    private final boolean tokenEndpoint;
    private final Duration endpointConnectionTtl;
    private final List<String> remoteAddressHeaders;
    private final List<String> remotePortHeaders;
    private final Set<String> knownServerNames;
    private final List<EntityLoggingEntry> entityLoggingEntries;
    private final Set<String> httpComplianceViolations;

    public static Builder builder(String name, int listenPort) { return new Builder(name, listenPort); }

    private HostedSslConnectorFactory(Builder builder) {
        super(new ConnectorFactory.Builder("tls"+builder.port, builder.port).sslProvider(createSslProvider(builder)));
        this.clientAuth = builder.clientAuth;
        this.tlsCiphersOverride = List.copyOf(builder.tlsCiphersOverride);
        this.proxyProtocolEnabled = builder.proxyProtocolEnabled;
        this.tokenEndpoint = builder.tokenEndpoint;
        this.endpointConnectionTtl = builder.endpointConnectionTtl;
        this.remoteAddressHeaders = List.copyOf(builder.remoteAddressHeaders);
        this.remotePortHeaders = List.copyOf(builder.remotePortHeaders);
        this.knownServerNames = Collections.unmodifiableSet(new TreeSet<>(builder.knownServerNames));
        this.entityLoggingEntries = builder.requestPrefixForLoggingContent.stream()
                .map(prefix -> {
                    var parts = prefix.split(":");
                    if (parts.length != 3) {
                        throw new IllegalArgumentException(Text.format("Expected string of format 'prefix:sample-rate:max-entity-size', got '%s'", prefix));
                    }
                    var pathPrefix = parts[0];
                    if (pathPrefix.isBlank())
                        throw new IllegalArgumentException("Path prefix must not be blank");
                    var sampleRate = Double.parseDouble(parts[1]);
                    if (sampleRate < 0 || sampleRate > 1)
                        throw new IllegalArgumentException(Text.format("Sample rate must be in range [0, 1], got '%s'", sampleRate));
                    var maxEntitySize = BytesQuantity.fromString(parts[2]);
                    return new EntityLoggingEntry(pathPrefix, sampleRate, maxEntitySize);
                })
                .toList();
        this.httpComplianceViolations = Set.copyOf(builder.httpComplianceViolations);
    }

    private static SslProvider createSslProvider(Builder builder) {
        if (builder.endpointCertificate == null) return new DefaultSslProvider(builder.name);
        var sslClientAuth = builder.clientAuth == SslClientAuth.NEED
                ? ConnectorConfig.Ssl.ClientAuth.Enum.NEED_AUTH : ConnectorConfig.Ssl.ClientAuth.Enum.WANT_AUTH;
        return new CloudSslProvider(
                builder.name, builder.endpointCertificate.key(), builder.endpointCertificate.certificate(),
                builder.tlsCaCertificatesPath, builder.tlsCaCertificatesPem, sslClientAuth, builder.tokenEndpoint);
    }

    @Override
    public void getConfig(ConnectorConfig.Builder connectorBuilder) {
        super.getConfig(connectorBuilder);
        if (clientAuth == SslClientAuth.WANT_WITH_ENFORCER) {
            connectorBuilder.tlsClientAuthEnforcer(
                    new ConnectorConfig.TlsClientAuthEnforcer.Builder()
                            .pathWhitelist(List.of("/status.html")).enable(true));
        }
        // Disables TLSv1.3 as it causes some browsers to prompt user for client certificate (when connector has 'want' auth)
        connectorBuilder.ssl.enabledProtocols(List.of("TLSv1.2"));
        if (!tlsCiphersOverride.isEmpty()) {
            connectorBuilder.ssl.enabledCipherSuites(tlsCiphersOverride.stream().sorted().toList());
        } else {
            connectorBuilder.ssl.enabledCipherSuites(TlsContext.ALLOWED_CIPHER_SUITES.stream().sorted().toList());
        }
        connectorBuilder
                .proxyProtocol(new ConnectorConfig.ProxyProtocol.Builder()
                                       .enabled(proxyProtocolEnabled))
                .idleTimeout(tokenEndpoint ? Duration.ofMinutes(5).toSeconds() : Duration.ofSeconds(30).toSeconds())
                .maxConnectionLife(endpointConnectionTtl != null ? endpointConnectionTtl.toSeconds() : 0)
                .accessLog(new ConnectorConfig.AccessLog.Builder()
                                   .remoteAddressHeaders(remoteAddressHeaders)
                                   .remotePortHeaders(remotePortHeaders)
                                   .content(entityLoggingEntries.stream()
                                                    .map(e -> new ConnectorConfig.AccessLog.Content.Builder()
                                                            .pathPrefix(e.prefix)
                                                            .sampleRate(e.sampleRate)
                                                            .maxSize(e.maxEntitySize.toBytes()))
                                                    .toList()))
                .compliance(new ConnectorConfig.Compliance.Builder()
                        .httpViolations(httpComplianceViolations))
                .serverName.known(knownServerNames);

    }

    public enum SslClientAuth { WANT, NEED, WANT_WITH_ENFORCER }
    public static class Builder {
        final String name;
        final int port;
        final List<String> remoteAddressHeaders = new ArrayList<>();
        final List<String> remotePortHeaders = new ArrayList<>();
        SslClientAuth clientAuth;
        List<String> tlsCiphersOverride = List.of();
        boolean proxyProtocolEnabled;
        Duration endpointConnectionTtl;
        EndpointCertificateSecrets endpointCertificate;
        String tlsCaCertificatesPem;
        String tlsCaCertificatesPath;
        boolean tokenEndpoint;
        Set<String> knownServerNames = Set.of();
        Set<String> requestPrefixForLoggingContent = Set.of();
        Set<String> httpComplianceViolations = Set.of();

        private Builder(String name, int port) { this.name = name; this.port = port; }
        public Builder clientAuth(SslClientAuth auth) { clientAuth = auth; return this; }
        public Builder endpointConnectionTtl(Duration ttl) { endpointConnectionTtl = ttl; return this; }
        public Builder tlsCiphersOverride(Collection<String> ciphers) { tlsCiphersOverride = List.copyOf(ciphers); return this; }
        public Builder proxyProtocol(boolean enabled) { proxyProtocolEnabled = enabled; return this; }
        public Builder endpointCertificate(EndpointCertificateSecrets cert) { this.endpointCertificate = cert; return this; }
        public Builder tlsCaCertificatesPath(String path) { this.tlsCaCertificatesPath = path; return this; }
        public Builder tlsCaCertificatesPem(String pem) { this.tlsCaCertificatesPem = pem; return this; }
        public Builder tokenEndpoint(boolean enable) { this.tokenEndpoint = enable; return this; }
        public Builder remoteAddressHeader(String header) { this.remoteAddressHeaders.add(header); return this; }
        public Builder remotePortHeader(String header) { this.remotePortHeaders.add(header); return this; }
        public Builder knownServerNames(Set<String> knownServerNames) { this.knownServerNames = Set.copyOf(knownServerNames); return this; }
        public Builder requestPrefixForLoggingContent(Collection<String> v) { this.requestPrefixForLoggingContent = Set.copyOf(v); return this; }
        public Builder httpComplianceViolations(Collection<String> v) { this.httpComplianceViolations = Set.copyOf(v); return this; }
        public HostedSslConnectorFactory build() { return new HostedSslConnectorFactory(this); }
    }
}
