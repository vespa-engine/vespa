// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

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
public class CloudSslContextProvider extends ConfiguredSslContextFactoryProvider {

    private final DataplaneProxyCredentials dataplaneProxyCredentials;

    public CloudSslContextProvider(ConnectorConfig connectorConfig, DataplaneProxyCredentials dataplaneProxyCredentials) {
        super(connectorConfig);
        this.dataplaneProxyCredentials = dataplaneProxyCredentials;
    }

    @Override
    Optional<String> getCaCertificates(ConnectorConfig.Ssl sslConfig) {
        String proxyCert;
        try {
            proxyCert = Files.readString(dataplaneProxyCredentials.certificateFile(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Dataplane proxy certificate not available", e);
        }
        if (!sslConfig.caCertificate().isBlank()) {
            return Optional.of(sslConfig.caCertificate() + "\n" + proxyCert);
        } else if (!sslConfig.caCertificateFile().isBlank()) {
            return Optional.of(readToString(sslConfig.caCertificateFile()) + "\n" + proxyCert);
        } else {
            return Optional.of(proxyCert);
        }
    }
}
