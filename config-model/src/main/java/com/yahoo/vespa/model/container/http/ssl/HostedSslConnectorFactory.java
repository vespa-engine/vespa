// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.vespa.model.container.http.ConnectorFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * Component specification for {@link com.yahoo.jdisc.http.server.jetty.ConnectorFactory} with hosted specific configuration.
 *
 * @author bjorncs
 */
public class HostedSslConnectorFactory extends ConnectorFactory {

    boolean requireTlsClientAuthDuringTlsHandshake;
    private final List<String> tlsCiphersOverride;
    private final boolean enableProxyProtocolMixedMode;
    private final Duration endpointConnectionTtl;

    public static Builder builder(String name, int listenPort) { return new Builder(name, listenPort); }

    private HostedSslConnectorFactory(Builder builder) {
        super(new ConnectorFactory.Builder("tls"+builder.port, builder.port).sslProvider(createSslProvider(builder)));
        this.requireTlsClientAuthDuringTlsHandshake = builder.requireTlsClientAuthDuringTlsHandshake;
        this.tlsCiphersOverride = List.copyOf(builder.tlsCiphersOverride);
        this.enableProxyProtocolMixedMode = builder.enableProxyProtocolMixedMode;
        this.endpointConnectionTtl = builder.endpointConnectionTtl;
    }

    private static SslProvider createSslProvider(Builder builder) {
        if (builder.endpointCertificate == null) return new DefaultSslProvider(builder.name);
        var clientAuthentication = builder.requireTlsClientAuthDuringTlsHandshake
                ? ConnectorConfig.Ssl.ClientAuth.Enum.NEED_AUTH : ConnectorConfig.Ssl.ClientAuth.Enum.WANT_AUTH;
        return new CloudSslProvider(
                builder.name, builder.endpointCertificate.key(), builder.endpointCertificate.certificate(),
                builder.tlsCaCertificatesPath, builder.tlsCaCertificatesPem, clientAuthentication);
    }

    @Override
    public void getConfig(ConnectorConfig.Builder connectorBuilder) {
        super.getConfig(connectorBuilder);
        if (! requireTlsClientAuthDuringTlsHandshake) {
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
                .proxyProtocol(new ConnectorConfig.ProxyProtocol.Builder().enabled(true).mixedMode(enableProxyProtocolMixedMode))
                .idleTimeout(Duration.ofSeconds(30).toSeconds())
                .maxConnectionLife(endpointConnectionTtl != null ? endpointConnectionTtl.toSeconds() : 0);
    }

    public static class Builder {
        final String name;
        final int port;
        boolean requireTlsClientAuthDuringTlsHandshake;
        List<String> tlsCiphersOverride;
        boolean enableProxyProtocolMixedMode;
        Duration endpointConnectionTtl;
        EndpointCertificateSecrets endpointCertificate;
        String tlsCaCertificatesPem;
        String tlsCaCertificatesPath;

        private Builder(String name, int port) { this.name = name; this.port = port; }
        public Builder requireTlsClientAuthDuringTlsHandshake(boolean enable) {this.requireTlsClientAuthDuringTlsHandshake = enable; return this; }
        public Builder endpointConnectionTtl(Duration ttl) { endpointConnectionTtl = ttl; return this; }
        public Builder tlsCiphersOverride(Collection<String> ciphers) { tlsCiphersOverride = List.copyOf(ciphers); return this; }
        public Builder proxyProtocolMixedMode(boolean enable) { enableProxyProtocolMixedMode = enable; return this; }
        public Builder endpointCertificate(EndpointCertificateSecrets cert) { this.endpointCertificate = cert; return this; }
        public Builder tlsCaCertificatesPath(String path) { this.tlsCaCertificatesPath = path; return this; }
        public Builder tlsCaCertificatesPem(String pem) { this.tlsCaCertificatesPem = pem; return this; }

        public HostedSslConnectorFactory build() { return new HostedSslConnectorFactory(this); }
    }
}
