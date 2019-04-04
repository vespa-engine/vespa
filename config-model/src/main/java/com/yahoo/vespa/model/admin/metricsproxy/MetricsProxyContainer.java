/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensionsConfig;
import ai.vespa.metricsproxy.rpc.RpcConnector;
import ai.vespa.metricsproxy.rpc.RpcConnectorConfig;
import ai.vespa.metricsproxy.service.VespaServices;
import ai.vespa.metricsproxy.service.VespaServicesConfig;
import com.yahoo.config.model.api.container.ContainerServiceType;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.vespa.model.container.Container;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.yahoo.config.model.api.container.ContainerServiceType.METRICS_PROXY_CONTAINER;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainer.NodeDimensionNames.CANONICAL_FLAVOR;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainer.NodeDimensionNames.CLUSTER_ID;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainer.NodeDimensionNames.CLUSTER_TYPE;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainer.NodeDimensionNames.FLAVOR;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.METRICS_PROXY_BUNDLE_NAME;

/**
 * Container running a metrics proxy.
 *
 * @author gjoranv
 */
public class MetricsProxyContainer extends Container implements
        NodeDimensionsConfig.Producer,
        RpcConnectorConfig.Producer,
        VespaServicesConfig.Producer
{

    static final class NodeDimensionNames {
        static final String FLAVOR = "flavor";
        static final String CANONICAL_FLAVOR = "canonicalFlavor";
        static final String CLUSTER_TYPE = "clustertype";
        static final String CLUSTER_ID = "clusterid";
    }

    private final boolean isHostedVespa;

    public MetricsProxyContainer(AbstractConfigProducer parent, int index, boolean isHostedVespa) {
        super(parent, "" + index, index);
        this.isHostedVespa = isHostedVespa;
        setProp("clustertype", "admin");
        setProp("index", String.valueOf(index));
        addNodeSpecificComponents();
    }

    private void addNodeSpecificComponents() {
        addMetricsProxyComponent(NodeDimensions.class);
        addMetricsProxyComponent(RpcConnector.class);
        addMetricsProxyComponent(VespaServices.class);
    }

    @Override
    protected ContainerServiceType myServiceType() {
        return METRICS_PROXY_CONTAINER;
    }

    @Override
    public int getWantedPort() {
        return 19092; // TODO: current metrics-proxy uses 19091 as rpc port, will now get 19093.
    }

    @Override
    public boolean requiresWantedPort() {
        return true;
    }

    @Override
    public int getPortCount() {
        return super.getPortCount() + 1;
    }

    @Override
    protected void tagServers() {
        super.tagServers();
        portsMeta.on(numHttpServerPorts).tag("rpc").tag("metrics");
    }

    @Override
    public void getConfig(RpcConnectorConfig.Builder builder) {
        builder.port(getRelativePort(0));
    }

    @Override
    public void getConfig(VespaServicesConfig.Builder builder) {
        builder.service.addAll(VespaServicesConfigGenerator.generate(getHostResource().getServices()));
    }

    @Override
    public void getConfig(NodeDimensionsConfig.Builder builder) {
        Map<String, String> dimensions = new LinkedHashMap<>();
        if (isHostedVespa) {
            getHostResource().getFlavor().ifPresent(flavor -> {
                dimensions.put(FLAVOR, flavor.name());
                dimensions.put(CANONICAL_FLAVOR, flavor.canonicalName());
            });

            getHostResource().primaryClusterMembership().map(ClusterMembership::cluster).ifPresent(cluster -> {
                dimensions.put(CLUSTER_TYPE, cluster.type().name());
                dimensions.put(CLUSTER_ID, cluster.id().value());
            });

            builder.dimensions(dimensions);
        }
    }

    private void  addMetricsProxyComponent(Class<?> componentClass) {
        addSimpleComponent(componentClass.getName(), null, METRICS_PROXY_BUNDLE_NAME);
    }

}
