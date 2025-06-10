// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentId;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.AccessLogComponent;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.http.ssl.DefaultSslProvider;
import com.yahoo.vespa.model.container.http.ssl.SslProvider;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private final Set<AccessLogComponent.RequestContentItem> requestContentLogging = new HashSet<>();

    protected ConnectorFactory(Builder builder) {
        super(new ComponentModel(builder.name,
                                 com.yahoo.jdisc.http.server.jetty.ConnectorFactory.class.getName(),
                                 null));
        this.name = builder.name;
        this.listenPort = builder.listenPort;
        this.sslProviderComponent = builder.sslProvider != null ? builder.sslProvider : new DefaultSslProvider(name);
        this.defaultRequestFilterChain = builder.defaultRequestFilterChain;
        this.defaultResponseFilterChain = builder.defaultResponseFilterChain;
        addChild(sslProviderComponent);
        inject(sslProviderComponent);
    }

    @Override
    public void getConfig(ConnectorConfig.Builder connectorBuilder) {
        connectorBuilder.listenPort(listenPort);
        connectorBuilder.name(name);
        connectorBuilder
                .accessLog(new ConnectorConfig.AccessLog.Builder()
                .remoteAddressHeaders(List.of("x-forwarded-for"))
                .remotePortHeaders(List.of("X-Forwarded-Port"))
                .content(requestContentLogging.stream()
                        .map(item -> new ConnectorConfig.AccessLog.Content.Builder()
                                .sampleRate(item.samplesPerSecond())
                                .pathPrefix(item.pathPrefix())
                                .maxSize(item.maxBytes()))
                        .toList()));
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

    public void addRequestContentLogging(Set<AccessLogComponent.RequestContentItem> requestContent) {
        requestContentLogging.addAll(requestContent);
    }

    public SslProvider sslProvider() { return sslProviderComponent; }

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

        public Builder sslProvider(SslProvider sslProvider) {
            this.sslProvider = sslProvider; return this;
        }

        public Builder defaultRequestFilterChain(ComponentId filterChain) {
            this.defaultRequestFilterChain = filterChain; return this;
        }

        public Builder defaultResponseFilterChain(ComponentId filterChain) {
            this.defaultResponseFilterChain = filterChain; return this;
        }

        public ConnectorFactory build() {
            return new ConnectorFactory(this);
        }
    }
}
