// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.core.MonitoringConfig;
import ai.vespa.metricsproxy.core.VespaMetrics;
import ai.vespa.metricsproxy.http.metrics.MetricsV1Handler;
import ai.vespa.metricsproxy.http.application.ApplicationMetricsHandler;
import ai.vespa.metricsproxy.http.application.ApplicationMetricsRetriever;
import ai.vespa.metricsproxy.http.application.MetricsNodesConfig;
import ai.vespa.metricsproxy.http.yamas.YamasHandler;
import ai.vespa.metricsproxy.http.prometheus.PrometheusHandler;
import ai.vespa.metricsproxy.metric.ExternalMetrics;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensionsConfig;
import ai.vespa.metricsproxy.rpc.RpcServer;
import ai.vespa.metricsproxy.service.ConfigSentinelClient;
import ai.vespa.metricsproxy.service.SystemPollerProvider;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.monitoring.MetricSet;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Handler;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static com.yahoo.vespa.model.admin.metricsproxy.ConsumersConfigGenerator.addMetrics;
import static com.yahoo.vespa.model.admin.metricsproxy.ConsumersConfigGenerator.generateConsumers;
import static com.yahoo.vespa.model.admin.metricsproxy.ConsumersConfigGenerator.toConsumerBuilder;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.APPLICATION;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.APPLICATION_ID;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.INSTANCE;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.LEGACY_APPLICATION;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.TENANT;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.ZONE;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.SYSTEM;
import static com.yahoo.vespa.model.admin.monitoring.DefaultPublicConsumer.getDefaultPublicConsumer;
import static com.yahoo.vespa.model.admin.monitoring.MetricSet.emptyMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.VespaMetricsConsumer.getVespaMetricsConsumer;
import static com.yahoo.vespa.model.container.xml.BundleMapper.JarSuffix.JAR_WITH_DEPS;
import static com.yahoo.vespa.model.container.xml.BundleMapper.absoluteBundlePath;

/**
 * Container cluster for metrics proxy containers.
 *
 * @author gjoranv
 */
public class MetricsProxyContainerCluster extends ContainerCluster<MetricsProxyContainer> implements
        ApplicationDimensionsConfig.Producer,
        ConsumersConfig.Producer,
        MonitoringConfig.Producer,
        ThreadpoolConfig.Producer,
        MetricsNodesConfig.Producer
{
    public static final Logger log = Logger.getLogger(MetricsProxyContainerCluster.class.getName());

    private static final String METRICS_PROXY_NAME = "metrics-proxy";
    static final Path METRICS_PROXY_BUNDLE_FILE = absoluteBundlePath((Paths.get(METRICS_PROXY_NAME + JAR_WITH_DEPS.suffix)));
    static final String METRICS_PROXY_BUNDLE_NAME = "com.yahoo.vespa." + METRICS_PROXY_NAME;

    static final class AppDimensionNames {
        static final String SYSTEM = "system";
        static final String ZONE = "zone";
        static final String APPLICATION_ID = "applicationId";  // tenant.app.instance
        static final String TENANT = "tenantName";
        static final String APPLICATION = "applicationName";
        static final String INSTANCE = "instanceName";
        static final String LEGACY_APPLICATION = "app";        // app.instance
    }

    private final AbstractConfigProducer<?> parent;
    private final ApplicationId applicationId;


    public MetricsProxyContainerCluster(AbstractConfigProducer<?> parent, String name, DeployState deployState) {
        super(parent, name, name, deployState);
        this.parent = parent;
        applicationId = deployState.getProperties().applicationId();

        setRpcServerEnabled(true);
        addDefaultHandlersExceptStatus();

        addPlatformBundle(METRICS_PROXY_BUNDLE_FILE);
        addClusterComponents();
    }

    private void addClusterComponents() {
        addMetricsProxyComponent(ApplicationDimensions.class);
        addMetricsProxyComponent(ConfigSentinelClient.class);
        addMetricsProxyComponent(ExternalMetrics.class);
        addMetricsProxyComponent(MetricsConsumers.class);
        addMetricsProxyComponent(MetricsManager.class);
        addMetricsProxyComponent(RpcServer.class);
        addMetricsProxyComponent(SystemPollerProvider.class);
        addMetricsProxyComponent(VespaMetrics.class);

        addHttpHandler(MetricsV1Handler.class, MetricsV1Handler.V1_PATH);
        addHttpHandler(PrometheusHandler.class, PrometheusHandler.V1_PATH);
        addHttpHandler(YamasHandler.class, YamasHandler.V1_PATH);

        addHttpHandler(ApplicationMetricsHandler.class, ApplicationMetricsHandler.V1_PATH);
        addMetricsProxyComponent(ApplicationMetricsRetriever.class);
    }

    private void addHttpHandler(Class<? extends ThreadedHttpRequestHandler> clazz, String bindingPath) {
        Handler<AbstractConfigProducer<?>> metricsHandler = new Handler<>(
                new ComponentModel(clazz.getName(), null, METRICS_PROXY_BUNDLE_NAME, null));
        metricsHandler.addServerBindings("http://*" + bindingPath,
                                         "http://*" + bindingPath + "/*");
        addComponent(metricsHandler);
    }

    @Override
    protected void doPrepare(DeployState deployState) { }

    @Override
    public void getConfig(MetricsNodesConfig.Builder builder) {
        builder.node.addAll(MetricsNodesConfigGenerator.generate(getContainers()));
    }

    @Override
    public void getConfig(MonitoringConfig.Builder builder) {
        getSystemName().ifPresent(builder::systemName);
        getIntervalMinutes().ifPresent(builder::intervalMinutes);
    }

    @Override
    public void getConfig(ConsumersConfig.Builder builder) {
        var amendedVespaConsumer = addMetrics(getVespaMetricsConsumer(), getAdditionalDefaultMetrics().getMetrics());
        builder.consumer.addAll(generateConsumers(amendedVespaConsumer, getUserMetricsConsumers()));

        builder.consumer.add(toConsumerBuilder(getDefaultPublicConsumer()));
    }

    @Override
    public void getConfig(ApplicationDimensionsConfig.Builder builder) {
        if (isHostedVespa()) {
            builder.dimensions(applicationDimensions());
        }
    }

    @Override
    public void getConfig(ThreadpoolConfig.Builder builder) {
        builder.maxthreads(10);
    }

    protected boolean messageBusEnabled() { return false; }

    private MetricSet getAdditionalDefaultMetrics() {
        return getAdmin()
                .map(Admin::getAdditionalDefaultMetrics)
                .orElse(emptyMetricSet());
    }

    // Returns the metrics consumers from services.xml
    private Map<String, MetricsConsumer> getUserMetricsConsumers() {
        return getAdmin()
                .map(admin -> admin.getUserMetrics().getConsumers())
                .orElse(Collections.emptyMap());
    }

    private Optional<Admin> getAdmin() {
        if (parent != null) {
            AbstractConfigProducerRoot r = parent.getRoot();
            if (r instanceof VespaModel) {
                VespaModel model = (VespaModel) r;
                return Optional.ofNullable(model.getAdmin());
            }
        }
        return Optional.empty();
    }

    private Optional<String> getSystemName() {
        Monitoring monitoring = getMonitoringService();
        return monitoring != null && ! monitoring.getClustername().equals("") ?
                Optional.of(monitoring.getClustername()) : Optional.empty();
    }

    private Optional<Integer> getIntervalMinutes() {
        Monitoring monitoring = getMonitoringService();
        return monitoring != null ?
                Optional.of(monitoring.getInterval()) : Optional.empty();
    }

    private void  addMetricsProxyComponent(Class<?> componentClass) {
        addSimpleComponent(componentClass.getName(), null, METRICS_PROXY_BUNDLE_NAME);
    }

    private Map<String, String> applicationDimensions() {
        Map<String, String> dimensions = new LinkedHashMap<>();
        dimensions.put(SYSTEM, getZone().system().value());
        dimensions.put(ZONE, zoneString(getZone()));
        dimensions.put(APPLICATION_ID, serializeWithDots(applicationId));
        dimensions.put(TENANT, applicationId.tenant().value());
        dimensions.put(APPLICATION, applicationId.application().value());
        dimensions.put(INSTANCE, applicationId.instance().value());
        dimensions.put(LEGACY_APPLICATION, applicationId.application().value() + "." + applicationId.instance().value());
        return dimensions;
    }

    // ApplicationId uses ':' as separator.
    private static String serializeWithDots(ApplicationId applicationId) {
        return applicationId.serializedForm().replace(':', '.');
    }

    static String zoneString(Zone zone) {
        return zone.environment().value() + "." + zone.region().value();
    }

}
