// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.server.jetty.DataplaneProxyCredentials;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

/**
 * Used to enable token based endpoints in Cloud. Amends trust store to allow proxy.
 *
 * @author mortent
 */
public class CloudTokenSslContextProvider extends ConfiguredSslContextFactoryProvider {

    private final DataplaneProxyCredentials dataplaneProxyCredentials;

    @Inject
    public CloudTokenSslContextProvider(ConnectorConfig connectorConfig,
                                        DataplaneProxyCredentials dataplaneProxyCredentials) {
        super(connectorConfig);
        this.dataplaneProxyCredentials = dataplaneProxyCredentials;
    }

    @Override
    Optional<String> getCaCertificates(ConnectorConfig.Ssl sslConfig) {
        try {
            return Optional.of(Files.readString(dataplaneProxyCredentials.certificateFile(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("Dataplane proxy certificate not available", e);
        }
    }
}
