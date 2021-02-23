// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ConnectorConfig.Ssl.ClientAuth;
import com.yahoo.vespa.model.container.http.ConnectorFactory;

import java.time.Duration;
import java.util.List;

/**
 * Component specification for {@link com.yahoo.jdisc.http.server.jetty.ConnectorFactory} with hosted specific configuration.
 *
 * @author bjorncs
 */
public class HostedSslConnectorFactory extends ConnectorFactory {

    private static final List<String> INSECURE_WHITELISTED_PATHS = List.of("/status.html");
    private static final String DEFAULT_HOSTED_TRUSTSTORE = "/opt/yahoo/share/ssl/certs/athenz_certificate_bundle.pem";

    private final boolean enforceClientAuth;
    private final boolean enforceHandshakeClientAuth;

    /**
     * Create connector factory that uses a certificate provided by the config-model / configserver and default hosted Vespa truststore.
     */
    // TODO Enforce client authentication
    public static HostedSslConnectorFactory withProvidedCertificate(
            String serverName, EndpointCertificateSecrets endpointCertificateSecrets, boolean enforceHandshakeClientAuth) {
        return new HostedSslConnectorFactory(createConfiguredDirectSslProvider(serverName, endpointCertificateSecrets, DEFAULT_HOSTED_TRUSTSTORE, /*tlsCaCertificates*/null, enforceHandshakeClientAuth), false, enforceHandshakeClientAuth);
    }

    /**
     * Create connector factory that uses a certificate provided by the config-model / configserver and a truststore configured by the application.
     */
    public static HostedSslConnectorFactory withProvidedCertificateAndTruststore(
            String serverName, EndpointCertificateSecrets endpointCertificateSecrets, String tlsCaCertificates) {
        return new HostedSslConnectorFactory(createConfiguredDirectSslProvider(serverName, endpointCertificateSecrets, /*tlsCaCertificatesPath*/null, tlsCaCertificates, false), true, false);
    }

    /**
     * Create connector factory that uses the default certificate and truststore provided by Vespa (through Vespa-global TLS configuration).
     */
    public static HostedSslConnectorFactory withDefaultCertificateAndTruststore(String serverName) {
        return new HostedSslConnectorFactory(new DefaultSslProvider(serverName), true, false);
    }

    private HostedSslConnectorFactory(SslProvider sslProvider, boolean enforceClientAuth, boolean enforceHandshakeClientAuth) {
        super(new Builder("tls4443", 4443).sslProvider(sslProvider));
        this.enforceClientAuth = enforceClientAuth;
        this.enforceHandshakeClientAuth = enforceHandshakeClientAuth;
    }

    private static ConfiguredDirectSslProvider createConfiguredDirectSslProvider(
            String serverName, EndpointCertificateSecrets endpointCertificateSecrets, String tlsCaCertificatesPath, String tlsCaCertificates, boolean enforceHandshakeClientAuth) {
        var clientAuthentication = enforceHandshakeClientAuth ? ClientAuth.Enum.NEED_AUTH : ClientAuth.Enum.WANT_AUTH;
        return new ConfiguredDirectSslProvider(
                serverName,
                endpointCertificateSecrets.key(),
                endpointCertificateSecrets.certificate(),
                tlsCaCertificatesPath,
                tlsCaCertificates,
                clientAuthentication);
    }

    @Override
    public void getConfig(ConnectorConfig.Builder connectorBuilder) {
        super.getConfig(connectorBuilder);
        if (! enforceHandshakeClientAuth) {
            connectorBuilder
                    .tlsClientAuthEnforcer(new ConnectorConfig.TlsClientAuthEnforcer.Builder()
                            .pathWhitelist(INSECURE_WHITELISTED_PATHS)
                            .enable(enforceClientAuth));
        }
        // Disables TLSv1.3 as it causes some browsers to prompt user for client certificate (when connector has 'want' auth)
        connectorBuilder.ssl.enabledProtocols(List.of("TLSv1.2"));

        connectorBuilder
                .proxyProtocol(new ConnectorConfig.ProxyProtocol.Builder().enabled(true).mixedMode(true))
                .idleTimeout(Duration.ofMinutes(3).toSeconds())
                .maxConnectionLife(Duration.ofMinutes(10).toSeconds());
    }
}
