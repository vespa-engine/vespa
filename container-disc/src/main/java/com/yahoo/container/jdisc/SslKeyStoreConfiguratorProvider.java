// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.SecretStore;
import com.yahoo.jdisc.http.ssl.DefaultSslKeyStoreConfigurator;
import com.yahoo.jdisc.http.ssl.SslKeyStoreConfigurator;

/**
 * @author bjorncs
 */
public class SslKeyStoreConfiguratorProvider implements Provider<SslKeyStoreConfigurator> {

    private final SslKeyStoreConfigurator sslKeyStoreConfigurator;

    public SslKeyStoreConfiguratorProvider(ConnectorConfig config, SecretStore secretStore) {
        this.sslKeyStoreConfigurator = new DefaultSslKeyStoreConfigurator(config, secretStore);
    }

    @Override
    public SslKeyStoreConfigurator get() {
        return sslKeyStoreConfigurator;
    }

    @Override
    public void deconstruct() {

    }
}
