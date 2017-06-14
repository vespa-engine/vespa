// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.guiceModules;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ConnectorConfig.Builder;
import com.yahoo.jdisc.http.SecretStore;
import com.yahoo.jdisc.http.server.jetty.ConnectorFactory;
import com.yahoo.jdisc.http.server.jetty.TestDrivers;
import com.yahoo.jdisc.http.ssl.ReaderForPath;
import com.yahoo.jdisc.http.ssl.SslKeyStore;
import com.yahoo.jdisc.http.ssl.SslKeyStoreFactory;

/**
 * Guice module for test ConnectorFactories
 *
 * @author tonytv
 */
public class ConnectorFactoryRegistryModule implements Module {

    private final Builder connectorConfigBuilder;

    public ConnectorFactoryRegistryModule(Builder connectorConfigBuilder) {
        this.connectorConfigBuilder = connectorConfigBuilder;
    }

    public ConnectorFactoryRegistryModule() {
        this(new Builder());
    }

    @Provides
    public ComponentRegistry<ConnectorFactory> connectorFactoryComponentRegistry() {
        ComponentRegistry<ConnectorFactory> registry = new ComponentRegistry<>();
        registry.register(ComponentId.createAnonymousComponentId("connector-factory"),
                new StaticKeyDbConnectorFactory(new ConnectorConfig(connectorConfigBuilder)));

        registry.freeze();
        return registry;
    }

    @Override
    public void configure(Binder binder) {
    }

    private static class StaticKeyDbConnectorFactory extends ConnectorFactory {

        public StaticKeyDbConnectorFactory(ConnectorConfig connectorConfig) {
            super(connectorConfig, new ThrowingSslKeyStoreFactory(), new MockSecretStore());
        }

    }

    private static final class ThrowingSslKeyStoreFactory implements SslKeyStoreFactory {

        @Override
        public SslKeyStore createKeyStore(ReaderForPath certificateFile, ReaderForPath keyFile) {
            throw new UnsupportedOperationException("A SSL key store factory component is not available");
        }

        @Override
        public SslKeyStore createTrustStore(ReaderForPath certificateFile) {
            throw new UnsupportedOperationException("A SSL key store factory component is not available");
        }

    }

    private static final class MockSecretStore implements SecretStore {

        @Override
        public String getSecret(String key) {
            return TestDrivers.KEY_STORE_PASSWORD;
        }

    }

}
