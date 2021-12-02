// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty.testutils;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.RequestLog;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.server.jetty.FilterBindings;
import com.yahoo.jdisc.http.server.jetty.JettyHttpServer;
import com.yahoo.jdisc.http.server.jetty.VoidConnectionLog;
import com.yahoo.jdisc.http.server.jetty.VoidRequestLog;
import com.yahoo.security.SslContextBuilder;

import javax.net.ssl.SSLContext;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A {@link com.yahoo.jdisc.test.TestDriver} that is configured with {@link JettyHttpServer}.
 *
 * @author bjorncs
 */
public class TestDriver implements AutoCloseable {

    private final com.yahoo.jdisc.test.TestDriver jdiscCoreTestDriver;
    private final JettyHttpServer server;
    private final SSLContext sslContext;

    private TestDriver(Builder builder) {
        ServerConfig serverConfig =
                builder.serverConfig != null ? builder.serverConfig : new ServerConfig(new ServerConfig.Builder());
        ConnectorConfig connectorConfig =
                builder.connectorConfig != null ? builder.connectorConfig : new ConnectorConfig(new ConnectorConfig.Builder());
        Module baseModule = createBaseModule(serverConfig, connectorConfig);
        Module combinedModule =
                builder.extraGuiceModules.isEmpty() ? baseModule : Modules.override(baseModule).with(builder.extraGuiceModules);
        com.yahoo.jdisc.test.TestDriver jdiscCoreTestDriver =
                com.yahoo.jdisc.test.TestDriver.newSimpleApplicationInstance(combinedModule);
        ContainerBuilder containerBuilder = jdiscCoreTestDriver.newContainerBuilder();
        JettyHttpServer server = containerBuilder.getInstance(JettyHttpServer.class);
        containerBuilder.serverProviders().install(server);
        builder.handlers.forEach((binding, handler) -> containerBuilder.serverBindings().bind(binding, handler));
        jdiscCoreTestDriver.activateContainer(containerBuilder);
        server.start();
        this.jdiscCoreTestDriver = jdiscCoreTestDriver;
        this.server = server;
        this.sslContext = newSslContext(containerBuilder);
    }

    public static Builder newBuilder() { return new Builder(); }

    public SSLContext sslContext() { return sslContext; }
    public JettyHttpServer server() { return server; }

    @Override public void close() { shutdown(); }

    public boolean shutdown() {
        server.close();
        server.release();
        return jdiscCoreTestDriver.close();
    }

    private static SSLContext newSslContext(ContainerBuilder builder) {
        ConnectorConfig.Ssl sslConfig = builder.getInstance(ConnectorConfig.class).ssl();
        if (!sslConfig.enabled()) return null;

        return new SslContextBuilder()
                .withKeyStore(Paths.get(sslConfig.privateKeyFile()), Paths.get(sslConfig.certificateFile()))
                .withTrustStore(Paths.get(sslConfig.caCertificateFile()))
                .build();
    }

    private static Module createBaseModule(ServerConfig serverConfig, ConnectorConfig connectorConfig) {
        return Modules.combine(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ServerConfig.class).toInstance(serverConfig);
                        bind(ConnectorConfig.class).toInstance(connectorConfig);
                        bind(FilterBindings.class).toInstance(new FilterBindings.Builder().build());
                        bind(ConnectionLog.class).toInstance(new VoidConnectionLog());
                        bind(RequestLog.class).toInstance(new VoidRequestLog());
                    }
                },
                new ConnectorFactoryRegistryModule(connectorConfig));
    }

    public static class Builder {
        private final SortedMap<String, RequestHandler> handlers = new TreeMap<>();
        private final List<Module> extraGuiceModules = new ArrayList<>();
        private ServerConfig serverConfig;
        private ConnectorConfig connectorConfig;

        private Builder() {}

        public Builder withRequestHandler(String binding, RequestHandler handler) {
            this.handlers.put(binding, handler); return this;
        }

        public Builder withRequestHandler(RequestHandler handler) { return withRequestHandler("http://*/*", handler); }

        public Builder withServerConfig(ServerConfig config) { this.serverConfig = config; return this; }

        public Builder withConnectorConfig(ConnectorConfig config) { this.connectorConfig = config; return this; }

        public Builder withGuiceModule(Module module) { this.extraGuiceModules.add(module); return this; }

        public TestDriver build() { return new TestDriver(this); }

    }
}
