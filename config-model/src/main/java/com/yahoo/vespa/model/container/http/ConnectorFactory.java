// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentId;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.http.ssl.DefaultSslProvider;
import com.yahoo.vespa.model.container.http.ssl.SslProvider;

import java.util.Optional;

import static com.yahoo.component.ComponentSpecification.fromString;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 * @author mortent
 */
public class ConnectorFactory extends SimpleComponent implements ConnectorConfig.Producer {

    private final String name;
    private final int listenPort;
    private final SslProvider sslProviderComponent;
    private volatile ComponentId defaultRequestFilterChain;
    private volatile ComponentId defaultResponseFilterChain;

    protected ConnectorFactory(String name, int listenPort, SslProvider sslProviderComponent) {
        this(name, listenPort, sslProviderComponent, null, null);
    }

    protected ConnectorFactory(
            String name,
            int listenPort,
            SslProvider sslProviderComponent,
            ComponentId defaultRequestFilterChain,
            ComponentId defaultResponseFilterChain) {
        super(new ComponentModel(
                new BundleInstantiationSpecification(
                        new ComponentId(name),
                        fromString("com.yahoo.jdisc.http.server.jetty.ConnectorFactory"),
                        fromString("jdisc_http_service"))));
        this.name = name;
        this.listenPort = listenPort;
        this.sslProviderComponent = sslProviderComponent;
        this.defaultRequestFilterChain = defaultRequestFilterChain;
        this.defaultResponseFilterChain = defaultResponseFilterChain;
        addChild(sslProviderComponent);
        inject(sslProviderComponent);
    }

    @Override
    public void getConfig(ConnectorConfig.Builder connectorBuilder) {
        connectorBuilder.listenPort(listenPort);
        connectorBuilder.name(name);
        sslProviderComponent.amendConnectorConfig(connectorBuilder);
    }

    public String getName() {
        return name;
    }

    public int getListenPort() {
        return listenPort;
    }

    public Optional<ComponentId> getDefaultRequestFilterChain() { return Optional.ofNullable(defaultRequestFilterChain); }

    public Optional<ComponentId> getDefaultResponseFilterChain() { return Optional.ofNullable(defaultResponseFilterChain); }

    public void setDefaultRequestFilterChain(ComponentId filterChain) { this.defaultRequestFilterChain = filterChain; }

    public void setDefaultResponseFilterChain(ComponentId filterChain) { this.defaultResponseFilterChain = filterChain; }

    public static class Builder {
        private final String name;
        private final int listenPort;

        private SslProvider sslProvider;
        private ComponentId defaultRequestFilterChain;
        private ComponentId defaultResponseFilterChain;

        public Builder(String name, int listenPort) {
            this.name = name;
            this.listenPort = listenPort;
        }

        public Builder setSslProvider(SslProvider sslProvider) {
            this.sslProvider = sslProvider; return this;
        }

        public Builder setDefaultRequestFilterChain(ComponentId filterChain) {
            this.defaultRequestFilterChain = filterChain; return this;
        }

        public Builder setDefaultResponseFilterChain(ComponentId filterChain) {
            this.defaultResponseFilterChain = filterChain;
            return this;
        }

        public ConnectorFactory build() {
            SslProvider sslProvider = this.sslProvider != null ? this.sslProvider : new DefaultSslProvider(name);
            return new ConnectorFactory(name, listenPort, sslProvider, defaultRequestFilterChain, defaultResponseFilterChain);
        }
    }
}
