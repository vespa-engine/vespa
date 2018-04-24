// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import com.google.inject.Inject;
import com.yahoo.jdisc.http.ConnectorConfig;

/**
 * @author bjorncs
 */
public class DefaultSslTrustStoreConfigurator implements SslTrustStoreConfigurator {

    @SuppressWarnings("deprecation")
    private final com.yahoo.jdisc.http.SecretStore secretStore;
    private final ConnectorConfig.Ssl config;

    @Inject
    @SuppressWarnings("deprecation")
    public DefaultSslTrustStoreConfigurator(ConnectorConfig config, com.yahoo.jdisc.http.SecretStore secretStore) {
        validateConfig(config.ssl());
        this.secretStore = secretStore;
        this.config = config.ssl();
    }

    @Override
    public void configure(SslTrustStoreContext context) {
        if (!config.enabled()) return;
        String keyDbPassword = config.keyDbKey();
        if (!config.trustStorePath().isEmpty()) {
            String password = config.useTrustStorePassword() ? secretStore.getSecret(keyDbPassword) : null;
            context.updateTrustStore(config.trustStorePath(), config.trustStoreType().toString(), password);
        }
    }

    private static void validateConfig(ConnectorConfig.Ssl config) {
        if (!config.enabled()) return;
        if (!config.trustStorePath().isEmpty() && config.useTrustStorePassword() && config.keyDbKey().isEmpty()) {
            throw new IllegalArgumentException("Missing password for JKS truststore");
        }
    }

}
