// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.ConnectionLogComponent;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class JettyHttpServer extends SimpleComponent implements ServerConfig.Producer {

    private final ContainerCluster<?> cluster;
    private final List<ConnectorFactory> connectorFactories = new ArrayList<>();
    private final SortedSet<String> ignoredUserAgentsList = new TreeSet<>();

    public JettyHttpServer(String componentId, ContainerCluster<?> cluster, DeployState deployState) {
        super(new ComponentModel(componentId, com.yahoo.jdisc.http.server.jetty.JettyHttpServer.class.getName(), null));
        this.cluster = cluster;
        FilterBindingsProviderComponent filterBindingsProviderComponent = new FilterBindingsProviderComponent(componentId);
        addChild(filterBindingsProviderComponent);
        addChild(new SimpleComponent(childComponentModel(componentId, com.yahoo.jdisc.http.server.jetty.JettyHttpServerContext.class.getName())) {
            { inject(filterBindingsProviderComponent); }
        });
        for (String agent : deployState.featureFlags().ignoredHttpUserAgents()) {
            addIgnoredUserAgent(agent);
        }
    }

    public void addConnector(ConnectorFactory connectorFactory) {
        connectorFactories.add(connectorFactory);
        addChild(connectorFactory);
    }

    public List<ConnectorFactory> getConnectorFactories() {
        return Collections.unmodifiableList(connectorFactories);
    }

    public void addIgnoredUserAgent(String userAgent) {
        ignoredUserAgentsList.add(userAgent);
    }

    @Override
    public void getConfig(ServerConfig.Builder builder) {
        builder.metric(new ServerConfig.Metric.Builder()
                .monitoringHandlerPaths(List.of("/state/v1", "/status.html", "/metrics/v2"))
                .ignoredUserAgents(ignoredUserAgentsList)
                .searchHandlerPaths(List.of("/search"))
        );
        if (cluster.getAllComponents().stream().anyMatch(c -> c instanceof ConnectionLogComponent))
            builder.connectionLog(new ServerConfig.ConnectionLog.Builder().enabled(true));
        configureJettyThreadpool(builder);
        builder.stopTimeout(300);
    }

    private void configureJettyThreadpool(ServerConfig.Builder builder) {
        if (cluster == null) return;
        if (cluster instanceof ApplicationContainerCluster) {
            builder.minWorkerThreads(-1).maxWorkerThreads(-1);
        } else {
            builder.minWorkerThreads(4).maxWorkerThreads(4);
        }
    }

    static ComponentModel childComponentModel(String parentId, String className) {
        final ComponentSpecification classNameSpec = new ComponentSpecification(
                className);
        return new ComponentModel(new BundleInstantiationSpecification(
                classNameSpec.nestInNamespace(new ComponentId(parentId)),
                classNameSpec,
                null));
    }

    public static final class FilterBindingsProviderComponent extends SimpleComponent {
        public FilterBindingsProviderComponent(String parentId) {
            super(childComponentModel(parentId, "com.yahoo.container.jdisc.FilterBindingsProvider"));
        }
    }

}
