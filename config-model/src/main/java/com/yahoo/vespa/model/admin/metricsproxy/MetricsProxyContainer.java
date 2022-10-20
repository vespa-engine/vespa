// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.http.metrics.MetricsV2Handler;
import ai.vespa.metricsproxy.http.metrics.NodeInfoConfig;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensionsConfig;
import ai.vespa.metricsproxy.metric.dimensions.PublicDimensions;
import ai.vespa.metricsproxy.rpc.RpcConnector;
import ai.vespa.metricsproxy.rpc.RpcConnectorConfig;
import ai.vespa.metricsproxy.service.VespaServices;
import ai.vespa.metricsproxy.service.VespaServicesConfig;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.container.ContainerServiceType;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Zone;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.PortAllocBridge;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.component.AccessLogComponent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.config.model.api.container.ContainerServiceType.METRICS_PROXY_CONTAINER;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.METRICS_PROXY_BUNDLE_NAME;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.createMetricsHandler;

/**
 * Container running a metrics proxy.
 *
 * @author gjoranv
 */
public class MetricsProxyContainer extends Container implements
        NodeDimensionsConfig.Producer,
        NodeInfoConfig.Producer,
        RpcConnectorConfig.Producer,
        VespaServicesConfig.Producer,
        QrStartConfig.Producer
{
    public static final int BASEPORT = 19092;

    final boolean isHostedVespa;
    private final Optional<ClusterMembership> clusterMembership;
    private final MetricsProxyContainerCluster cluster;
    private final ApplicationId applicationId;
    private final Zone zone;
    private final String jvmGCOptions;

    public MetricsProxyContainer(MetricsProxyContainerCluster cluster, HostResource host, int index, DeployState deployState) {
        super(cluster, host.getHostname(), index, deployState);
        this.isHostedVespa = deployState.isHosted();
        this.clusterMembership = host.spec().membership();
        this.cluster = cluster;
        this.applicationId = deployState.getApplicationPackage().getApplicationId();
        this.zone = deployState.zone();
        this.jvmGCOptions = deployState.getProperties().jvmGCOptions(clusterMembership.map(membership -> membership.cluster().type()));
        setProp("clustertype", "admin");
        setProp("index", String.valueOf(index));
        addNodeSpecificComponents();
        addComponent(new AccessLogComponent(containerCluster().orElse(null), AccessLogComponent.AccessLogType.jsonAccessLog,
                "zstd",
                Optional.of("metrics-proxy"),
                deployState.isHosted()));
    }

    private void addNodeSpecificComponents() {
        addMetricsProxyComponent(NodeDimensions.class);
        addMetricsProxyComponent(RpcConnector.class);
        addMetricsProxyComponent(VespaServices.class);
        addHandler(createMetricsHandler(MetricsV2Handler.class, MetricsV2Handler.V2_PATH));
    }

    @Override
    protected ContainerServiceType myServiceType() {
        return METRICS_PROXY_CONTAINER;
    }

    @Override
    protected String jvmOmitStackTraceInFastThrowOption(ModelContext.FeatureFlags featureFlags) {
        return featureFlags.jvmOmitStackTraceInFastThrowOption(ClusterSpec.Type.admin);
    }

    @Override
    public int getWantedPort() {
        return BASEPORT;
    }

    @Override
    public boolean requiresWantedPort() {
        return true;
    }

    private int metricsRpcPort;

    // Must have predictable ports for both http and rpc.
    @Override
    public void allocatePorts(int start, PortAllocBridge from) {
        if (start == 0) start = BASEPORT;
        if (getHttp() != null) {
            throw new IllegalArgumentException("unexpected HTTP setup");
        }
        allocatedSearchPort = from.wantPort(start++, "http");
        portsMeta.on(0).tag("http").tag("query").tag("external").tag("state");

        // XXX remove:
        from.wantPort(start++, "http/1");
        portsMeta.on(1).tag("unused");

        if (numMessageBusPorts() != 0) {
            throw new IllegalArgumentException("expecting 0 message bus ports");
        }
        if (numRpcPorts() != 1) {
            throw new IllegalArgumentException("expecting 1 rpc port");
        }
        allocatedRpcPort = from.wantPort(start++, "rpc/admin");
        portsMeta.on(2).tag("rpc").tag("admin");
        metricsRpcPort = from.wantPort(start++, "rpc/metrics");
        portsMeta.on(3).tag("rpc").tag("metrics");
    }

    @Override
    public int getPortCount() {
        return 4;
    }

    @Override
    public void getConfig(RpcConnectorConfig.Builder builder) {
        builder.port(metricsRpcPort);
    }

    @Override
    public void getConfig(VespaServicesConfig.Builder builder) {
        builder.service.addAll(VespaServicesConfigGenerator.generate(getHostResource().getServices()));
    }

    @Override
    public void getConfig(NodeDimensionsConfig.Builder builder) {
        Map<String, String> dimensions = new LinkedHashMap<>();
        if (isHostedVespa) {
            getHostResource().spec().membership().map(ClusterMembership::cluster).ifPresent(cluster -> {
                dimensions.put(PublicDimensions.DEPLOYMENT_CLUSTER, getDeploymentCluster(cluster));
                dimensions.put(PublicDimensions.INTERNAL_CLUSTER_TYPE, cluster.type().name());
                dimensions.put(PublicDimensions.INTERNAL_CLUSTER_ID, cluster.id().value());
                cluster.group().ifPresent(group -> dimensions.put(PublicDimensions.GROUP_ID, group.toString()));
            });

            builder.dimensions(dimensions);
        }
    }

    @Override
    public void getConfig(NodeInfoConfig.Builder builder) {
        builder.role(getNodeRole())
                .hostname(getHostName());
    }

    @Override
    public void getConfig(QrStartConfig.Builder builder) {
        cluster.getConfig(builder);

        if (clusterMembership.isPresent()) {
            boolean adminCluster = clusterMembership.get().cluster().type() == ClusterSpec.Type.admin;
            int maxHeapSize = adminCluster ? 96 : 256;
            builder.jvm
                    .gcopts(jvmGCOptions)
                    .heapsize(maxHeapSize);
            if (adminCluster) builder.jvm.minHeapsize(maxHeapSize);
        }
    }

    private String getNodeRole() {
        String hostConfigId = getHost().getHost().getConfigId();
        if (! isHostedVespa) return hostConfigId;
        return getHostResource().spec().membership()
                    .map(ClusterMembership::stringValue)
                    .orElse(hostConfigId);
    }

    private void addMetricsProxyComponent(Class<?> componentClass) {
        addSimpleComponent(componentClass.getName(), null, METRICS_PROXY_BUNDLE_NAME);
    }

    private String getDeploymentCluster(ClusterSpec cluster) {
        return String.join(".", applicationId.toFullString(), zone.environment().value(), zone.region().value(), cluster.id().value());
    }

    @Override
    protected String defaultPreload() {
        return "";
    }

}
