// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.yahoo.component.ComponentSpecification.fromString;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class JettyHttpServer extends SimpleComponent implements ServerConfig.Producer {

    private final ContainerCluster<?> cluster;
    private final boolean isHostedVespa;
    private final List<ConnectorFactory> connectorFactories = new ArrayList<>();
    private final boolean enableJdiscConnectionLog;

    public JettyHttpServer(ComponentId id, ContainerCluster<?> cluster, ModelContext.FeatureFlags featureFlags, boolean isHostedVespa) {
        super(new ComponentModel(
                new BundleInstantiationSpecification(id,
                                                     fromString("com.yahoo.jdisc.http.server.jetty.JettyHttpServer"),
                                                     fromString("jdisc_http_service"))
        ));
        this.isHostedVespa = isHostedVespa;
        this.cluster = cluster;
        final FilterBindingsProviderComponent filterBindingsProviderComponent = new FilterBindingsProviderComponent(id);
        addChild(filterBindingsProviderComponent);
        inject(filterBindingsProviderComponent);
        this.enableJdiscConnectionLog = featureFlags.enableJdiscConnectionLog();
    }

    public void addConnector(ConnectorFactory connectorFactory) {
        connectorFactories.add(connectorFactory);
        addChild(connectorFactory);
    }

    public void removeConnector(ConnectorFactory connectorFactory) {
        if (connectorFactory == null) {
            return;
        }
        removeChild(connectorFactory);
        connectorFactories.remove(connectorFactory);
    }

    public List<ConnectorFactory> getConnectorFactories() {
        return Collections.unmodifiableList(connectorFactories);
    }

    @Override
    public void getConfig(ServerConfig.Builder builder) {
        builder.metric(new ServerConfig.Metric.Builder()
                .monitoringHandlerPaths(List.of("/state/v1", "/status.html"))
                .searchHandlerPaths(List.of("/search"))
        );
        if (isHostedVespa) {
            // Proxy-protocol v1/v2 is used in hosted Vespa for remote address/port
            builder.accessLog(new ServerConfig.AccessLog.Builder()
                    .remoteAddressHeaders(List.of())
                    .remotePortHeaders(List.of()));
        } else {
            // TODO Vespa 8: Remove legacy Yahoo headers
            builder.accessLog(new ServerConfig.AccessLog.Builder()
                    .remoteAddressHeaders(List.of("x-forwarded-for", "y-ra", "yahooremoteip", "client-ip"))
                    .remotePortHeaders(List.of("X-Forwarded-Port", "y-rp")));
        }
        configureJettyThreadpool(builder);
        builder.connectionLog(new ServerConfig.ConnectionLog.Builder()
                .enabled(enableJdiscConnectionLog));
    }

    private void configureJettyThreadpool(ServerConfig.Builder builder) {
        if (cluster == null) return;
        double vcpu = cluster.vcpu().orElse(0);
        if (vcpu > 0) {
            int threads = 16 + (int) Math.ceil(vcpu);
            builder.maxWorkerThreads(threads).minWorkerThreads(threads);
        }
    }

    static ComponentModel providerComponentModel(final ComponentId parentId, String className) {
        final ComponentSpecification classNameSpec = new ComponentSpecification(
                className);
        return new ComponentModel(new BundleInstantiationSpecification(
                classNameSpec.nestInNamespace(parentId),
                classNameSpec,
                null));
    }

    public static final class FilterBindingsProviderComponent extends SimpleComponent {
        public FilterBindingsProviderComponent(final ComponentId parentId) {
            super(providerComponentModel(parentId, "com.yahoo.container.jdisc.FilterBindingsProvider"));
        }

    }

}
