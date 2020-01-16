// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import com.yahoo.config.model.api.TlsSecrets;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ConnectorConfig.Ssl.ClientAuth;
import com.yahoo.vespa.model.container.http.ConnectorFactory;

import java.util.List;

/**
 * Component specification for {@link com.yahoo.jdisc.http.server.jetty.ConnectorFactory} with hosted specific configuration.
 *
 * @author bjorncs
 */
public class HostedSslConnectorFactory extends ConnectorFactory {

    private static final List<String> INSECURE_WHITELISTED_PATHS = List.of("/status.html");

    private final boolean enforceClientAuth;

    public HostedSslConnectorFactory(String serverName, TlsSecrets tlsSecrets) {
        this(serverName, tlsSecrets, null, false);
    }

    public HostedSslConnectorFactory(String serverName, TlsSecrets tlsSecrets, String tlsCaCertificates, boolean enforceClientAuth) {
        super("tls4443", 4443, createSslProvider(serverName, tlsSecrets, tlsCaCertificates));
        this.enforceClientAuth = enforceClientAuth;
    }

    private static ConfiguredDirectSslProvider createSslProvider(
            String serverName, TlsSecrets tlsSecrets, String tlsCaCertificates) {
        return new ConfiguredDirectSslProvider(
                serverName,
                tlsSecrets.key(),
                tlsSecrets.certificate(),
                /*caCertificatePath*/null,
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
