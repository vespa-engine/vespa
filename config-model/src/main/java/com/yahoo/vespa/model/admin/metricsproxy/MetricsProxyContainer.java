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
import com.yahoo.vespa.model.PortAllocBridge;
import com.yahoo.vespa.model.container.Container;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.yahoo.config.model.api.container.ContainerServiceType.METRICS_PROXY_CONTAINER;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainer.NodeDimensionNames.CLUSTER_ID;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainer.NodeDimensionNames.CLUSTER_TYPE;
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
        static final String CLUSTER_TYPE = "clustertype";
        static final String CLUSTER_ID = "clusterid";
    }

    private final boolean isHostedVespa;

    public MetricsProxyContainer(AbstractConfigProducer parent, String hostname, int index, boolean isHostedVespa) {
        super(parent, hostname, index);
        this.isHostedVespa = isHostedVespa;
        setProp("clustertype", "admin");
        setProp("index", String.valueOf(index));
        addNodeSpecificComponents();

        appendJvmOptions("-Xms32m -Xmx512m");
    }

    private void addNodeSpecificComponents() {
        addMetricsProxyComponent(NodeDimensions.class);
        addMetricsProxyComponent(RpcConnector.class);
        addMetricsProxyComponent(VespaServices.class);
    }

    int metricsRpcPortOffset() {
        int numHttpServerPorts = getNumHttpServerPorts();
        if (numHttpServerPorts != 2) {
            throw new IllegalArgumentException("expecting 2 http server ports");
        }
        if (numMessageBusPorts() != 0) {
            throw new IllegalArgumentException("expecting 0 message bus ports");
        }
        if (numRpcPorts() != 1) {
            throw new IllegalArgumentException("expecting 1 rpc port");
        }
        return numHttpServerPorts + numMessageBusPorts() + numRpcPorts();
    }

    @Override
    protected ContainerServiceType myServiceType() {
        return METRICS_PROXY_CONTAINER;
    }

    static public int BASEPORT = 19092;

    @Override
    public int getWantedPort() {
        return BASEPORT;
    }

    @Override
    public boolean requiresWantedPort() {
        return true;
    }

    // Must have predictable ports for both http and rpc.
    @Override
    public void allocatePorts(int start, PortAllocBridge from) {
        if (start == 0) start = BASEPORT;
        from.wantPort(start++, "http");
        from.wantPort(start++, "http/1");
        from.wantPort(start++, "rpc/admin");
        from.wantPort(start++, "rpc/metrics");
    }

    @Override
    public int getPortCount() {
        return metricsRpcPortOffset() + 1;
    }

    @Override
    protected void tagServers() {
        super.tagServers();
        portsMeta.on(metricsRpcPortOffset()).tag("rpc").tag("metrics");
    }

    @Override
    public void getConfig(RpcConnectorConfig.Builder builder) {
        builder.port(getRelativePort(metricsRpcPortOffset()));
    }

    @Override
    public void getConfig(VespaServicesConfig.Builder builder) {
        builder.service.addAll(VespaServicesConfigGenerator.generate(getHostResource().getServices()));
    }

    @Override
    public void getConfig(NodeDimensionsConfig.Builder builder) {
        Map<String, String> dimensions = new LinkedHashMap<>();
        if (isHostedVespa) {
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
