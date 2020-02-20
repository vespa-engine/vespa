// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ConnectorConfig.Ssl.ClientAuth;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.http.ConnectorFactory;

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

    /**
     * Create connector factory that uses a certificate provided by the config-model / configserver and default hosted Vespa truststore.
     */
    // TODO Enforce client authentication
    public static HostedSslConnectorFactory withProvidedCertificate(String serverName, EndpointCertificateSecrets endpointCertificateSecrets) {
        return new HostedSslConnectorFactory(
                createConfiguredDirectSslProvider(serverName, endpointCertificateSecrets, DEFAULT_HOSTED_TRUSTSTORE, /*tlsCaCertificates*/null), false);
    }

    /**
     * Create connector factory that uses a certificate provided by the config-model / configserver and a truststore configured by the application.
     */
    public static HostedSslConnectorFactory withProvidedCertificateAndTruststore(
            String serverName, EndpointCertificateSecrets endpointCertificateSecrets, String tlsCaCertificates) {
        return new HostedSslConnectorFactory(
                createConfiguredDirectSslProvider(serverName, endpointCertificateSecrets, /*tlsCaCertificatesPath*/null, tlsCaCertificates), true);
    }

    /**
     * Create connector factory that uses the default certificate and truststore provided by Vespa (through Vespa-global TLS configuration).
     */
    public static HostedSslConnectorFactory withDefaultCertificateAndTruststore(String serverName) {
        return new HostedSslConnectorFactory(new DefaultSslProvider(serverName), true);
    }

    private HostedSslConnectorFactory(SimpleComponent sslProviderComponent, boolean enforceClientAuth) {
        super("tls4443", 4443, sslProviderComponent);
        this.enforceClientAuth = enforceClientAuth;
    }

    private static ConfiguredDirectSslProvider createConfiguredDirectSslProvider(
            String serverName, EndpointCertificateSecrets endpointCertificateSecrets, String tlsCaCertificatesPath, String tlsCaCertificates) {
        return new ConfiguredDirectSslProvider(
                serverName,
                endpointCertificateSecrets.key(),
                endpointCertificateSecrets.certificate(),
                tlsCaCertificatesPath,
                tlsCaCertificates,
                ClientAuth.Enum.WANT_AUTH);
    }

    @Override
    public void getConfig(ConnectorConfig.Builder connectorBuilder) {
        super.getConfig(connectorBuilder);
        connectorBuilder.tlsClientAuthEnforcer(new ConnectorConfig.TlsClientAuthEnforcer.Builder()
                                                       .pathWhitelist(INSECURE_WHITELISTED_PATHS)
                                                       .enable(enforceClientAuth));
    }

}
