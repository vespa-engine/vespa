// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import com.yahoo.config.model.api.TlsSecrets;
import com.yahoo.vespa.model.container.http.ConnectorFactory;

/**
 * Component specification for {@link com.yahoo.jdisc.http.server.jetty.ConnectorFactory} with hosted specific configuration.
 *
 * @author bjorncs
 */
public class HostedSslConnectorFactory extends ConnectorFactory {

    public HostedSslConnectorFactory(String serverName, TlsSecrets tlsSecrets) {
        this(serverName, tlsSecrets, null);
    }

    public HostedSslConnectorFactory(String serverName, TlsSecrets tlsSecrets, String tlsCaCertificates) {
        super("tls4443", 4443, createSslProvider(serverName, tlsSecrets, tlsCaCertificates));
    }

    private static ConfiguredDirectSslProvider createSslProvider(
            String serverName, TlsSecrets tlsSecrets, String tlsCaCertificates) {
        return new ConfiguredDirectSslProvider(
                serverName,
                tlsSecrets.key(),
                tlsSecrets.certificate(),
                /*caCertificatePath*/null,
                tlsCaCertificates,
                "disabled");
    }

}
