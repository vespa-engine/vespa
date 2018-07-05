// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.yahoo.jdisc.application.BindingRepository;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.ServletPathsConfig;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;
import com.yahoo.jdisc.http.guiceModules.ConnectorFactoryRegistryModule;
import com.yahoo.jdisc.http.guiceModules.ServletModule;
import com.yahoo.jdisc.http.server.FilterBindings;

import java.io.IOException;

import static com.google.inject.name.Names.named;

/**
 * @author Simon Thoresen Hult
 */
public class TestDrivers {

    private static final String KEY_STORE = "src/test/resources/ssl_keystore_test.jks";
    public static final String KEY_STORE_PASSWORD = "secret";

    public static TestDriver newConfiguredInstance(final RequestHandler requestHandler,
                                                   final ServerConfig.Builder serverConfig,
                                                   final ConnectorConfig.Builder connectorConfig,
                                                   final Module... guiceModules) throws IOException {
        return TestDriver.newInstance(
                JettyHttpServer.class,
                requestHandler,
                newConfigModule(serverConfig, connectorConfig, guiceModules));
    }

    public static TestDriver newInstance(final RequestHandler requestHandler,
                                         final Module... guiceModules) throws IOException {
        return TestDriver.newInstance(
                JettyHttpServer.class,
                requestHandler,
                newConfigModule(
                        new ServerConfig.Builder(),
                        new ConnectorConfig.Builder(),
                        guiceModules
                ));
    }

    public static TestDriver newInstanceWithSsl(final RequestHandler requestHandler,
                                                final Module... guiceModules) throws IOException {
        return TestDriver.newInstance(
                JettyHttpServer.class,
                requestHandler,
                newConfigModule(
                        new ServerConfig.Builder(),
                        new ConnectorConfig.Builder()
                                .ssl(new ConnectorConfig.Ssl.Builder()
                                             .enabled(true)
                                             .keyDbKey("dummy-key-for-StaticKeyDbConnectorFactory.getPasswordFromKeydb")
                                             .keyStorePath(KEY_STORE)
                                             .trustStorePath(KEY_STORE)),
                        Modules.combine(new AbstractModule() {

                            @Override
                            protected void configure() {
                                bind(String.class).annotatedWith(named("keyStorePassword"))
                                        .toInstance(KEY_STORE_PASSWORD);
                            }
                        }, Modules.combine(guiceModules))
                ));
    }

    private static Module newConfigModule(
            final ServerConfig.Builder serverConfig,
            final ConnectorConfig.Builder connectorConfigBuilder,
            final Module... guiceModules) {
        return Modules.combine(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ServletPathsConfig.class).toInstance(new ServletPathsConfig(new ServletPathsConfig.Builder()));
                        bind(ServerConfig.class).toInstance(new ServerConfig(serverConfig));
                        bind(ConnectorConfig.class).toInstance(new ConnectorConfig(connectorConfigBuilder));
                        bind(FilterBindings.class).toInstance(
                                new FilterBindings(
                                        new BindingRepository<RequestFilter>(),
                                        new BindingRepository<ResponseFilter>()));
                    }
                },
                new ConnectorFactoryRegistryModule(connectorConfigBuilder),
                new ServletModule(),
                Modules.combine(guiceModules));
    }
}
