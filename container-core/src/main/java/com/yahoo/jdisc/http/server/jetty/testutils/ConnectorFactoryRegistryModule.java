// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty.testutils;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.jdisc.http.ConnectorConfig;

import com.yahoo.jdisc.http.server.jetty.ConnectorFactory;
import com.yahoo.jdisc.http.ssl.impl.ConfiguredSslContextFactoryProvider;

/**
 * Guice module for test ConnectorFactories
 *
 * @author Tony Vaagenes
 */
public class ConnectorFactoryRegistryModule implements Module {

    private final ConnectorConfig config;

    public ConnectorFactoryRegistryModule(ConnectorConfig config) {
        this.config = config;
    }

    public ConnectorFactoryRegistryModule() {
        this(new ConnectorConfig(new ConnectorConfig.Builder()));
    }

    @SuppressWarnings("unused")
    @Provides
    public ComponentRegistry<ConnectorFactory> connectorFactoryComponentRegistry() {
        ComponentRegistry<ConnectorFactory> registry = new ComponentRegistry<>();
        registry.register(ComponentId.createAnonymousComponentId("connector-factory"),
                new StaticKeyDbConnectorFactory(config));

        registry.freeze();
        return registry;
    }

    @Override public void configure(Binder binder) {}

    private static class StaticKeyDbConnectorFactory extends ConnectorFactory {

        public StaticKeyDbConnectorFactory(ConnectorConfig connectorConfig) {
            super(connectorConfig, new ConfiguredSslContextFactoryProvider(connectorConfig));
        }

    }

}
