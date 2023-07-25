// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.vespa.model.container.http.ConnectorFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Component specification for {@link com.yahoo.jdisc.http.server.jetty.ConnectorFactory} with hosted specific configuration.
 *
 * @author bjorncs
 */
public class HostedSslConnectorFactory extends ConnectorFactory {

    private final SslClientAuth clientAuth;
    private final List<String> tlsCiphersOverride;
    private final boolean proxyProtocolEnabled;
    private final boolean proxyProtocolMixedMode;
    private final Duration endpointConnectionTtl;
    private final List<String> remoteAddressHeaders;
    private final List<String> remotePortHeaders;

    public static Builder builder(String name, int listenPort) { return new Builder(name, listenPort); }

    private HostedSslConnectorFactory(Builder builder) {
        super(new ConnectorFactory.Builder("tls"+builder.port, builder.port).sslProvider(createSslProvider(builder)));
        this.clientAuth = builder.clientAuth;
        this.tlsCiphersOverride = List.copyOf(builder.tlsCiphersOverride);
        this.proxyProtocolEnabled = builder.proxyProtocolEnabled;
        this.proxyProtocolMixedMode = builder.proxyProtocolMixedMode;
        this.endpointConnectionTtl = builder.endpointConnectionTtl;
        this.remoteAddressHeaders = List.copyOf(builder.remoteAddressHeaders);
        this.remotePortHeaders = List.copyOf(builder.remotePortHeaders);
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
                                       .enabled(proxyProtocolEnabled).mixedMode(proxyProtocolMixedMode))
                .idleTimeout(Duration.ofSeconds(30).toSeconds())
                .maxConnectionLife(endpointConnectionTtl != null ? endpointConnectionTtl.toSeconds() : 0)
                .accessLog(new ConnectorConfig.AccessLog.Builder()
                                  .remoteAddressHeaders(remoteAddressHeaders)
                                  .remotePortHeaders(remotePortHeaders));

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
        boolean proxyProtocolMixedMode;
        Duration endpointConnectionTtl;
        EndpointCertificateSecrets endpointCertificate;
        String tlsCaCertificatesPem;
        String tlsCaCertificatesPath;
        boolean tokenEndpoint;

        private Builder(String name, int port) { this.name = name; this.port = port; }
        public Builder clientAuth(SslClientAuth auth) { clientAuth = auth; return this; }
        public Builder endpointConnectionTtl(Duration ttl) { endpointConnectionTtl = ttl; return this; }
        public Builder tlsCiphersOverride(Collection<String> ciphers) { tlsCiphersOverride = List.copyOf(ciphers); return this; }
        public Builder proxyProtocol(boolean enabled, boolean mixedMode) { proxyProtocolEnabled = enabled; proxyProtocolMixedMode = mixedMode; return this; }
        public Builder endpointCertificate(EndpointCertificateSecrets cert) { this.endpointCertificate = cert; return this; }
        public Builder tlsCaCertificatesPath(String path) { this.tlsCaCertificatesPath = path; return this; }
        public Builder tlsCaCertificatesPem(String pem) { this.tlsCaCertificatesPem = pem; return this; }
        public Builder tokenEndpoint(boolean enable) { this.tokenEndpoint = enable; return this; }
        public Builder remoteAddressHeader(String header) { this.remoteAddressHeaders.add(header); return this; }
        public Builder remotePortHeader(String header) { this.remotePortHeaders.add(header); return this; }

        public HostedSslConnectorFactory build() { return new HostedSslConnectorFactory(this); }
    }
}
