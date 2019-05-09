/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.metric.ExternalMetrics;
import ai.vespa.metricsproxy.core.VespaMetrics;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensionsConfig;
import ai.vespa.metricsproxy.rpc.RpcServer;
import ai.vespa.metricsproxy.core.MonitoringConfig;
import ai.vespa.metricsproxy.service.SystemPollerProvider;
import ai.vespa.metricsproxy.service.ConfigSentinelClient;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import com.yahoo.vespa.model.admin.monitoring.builder.Metrics;
import com.yahoo.vespa.model.container.ContainerCluster;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.APPLICATION;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.APPLICATION_ID;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.INSTANCE;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.LEGACY_APPLICATION;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.TENANT;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.ZONE;
import static com.yahoo.vespa.model.container.xml.BundleMapper.JarSuffix.JAR_WITH_DEPS;
import static com.yahoo.vespa.model.container.xml.BundleMapper.bundlePathFromName;

/**
 * Container cluster for metrics proxy containers.
 *
 * @author gjoranv
 */
public class MetricsProxyContainerCluster extends ContainerCluster<MetricsProxyContainer> implements
        ApplicationDimensionsConfig.Producer,
        ConsumersConfig.Producer,
        MonitoringConfig.Producer
{
    public static final Logger log = Logger.getLogger(MetricsProxyContainerCluster.class.getName());

    private static final String METRICS_PROXY_NAME = "metrics-proxy";
    private static final Path METRICS_PROXY_BUNDLE_FILE = bundlePathFromName(METRICS_PROXY_NAME, JAR_WITH_DEPS);
    static final String METRICS_PROXY_BUNDLE_NAME = "com.yahoo.vespa." + METRICS_PROXY_NAME;

    static final String DEFAULT_NAME_IN_MONITORING_SYSTEM = "vespa";
    static final int DEFAULT_MONITORING_INTERVAL = 1;

    static final class AppDimensionNames {
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

        setRpcServerEnabled(false);
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
    }

    @Override
    protected void doPrepare(DeployState deployState) { }

    @Override
    public void getConfig(MonitoringConfig.Builder builder) {
        builder.systemName(getSystemName())
                .intervalMinutes(getIntervalMinutes());
    }

    @Override
    public void getConfig(ConsumersConfig.Builder builder) {
        builder.consumer.addAll(ConsumersConfigGenerator.generate(getUserMetricsConsumers()));
    }

    @Override
    public void getConfig(ApplicationDimensionsConfig.Builder builder) {
        if (isHostedVespa()) {
            builder.dimensions(applicationDimensions());
        }
    }

    // Returns the metricConsumers from services.xml
    private Map<String, MetricsConsumer> getUserMetricsConsumers() {
        return getAdmin()
                .map(this::consumersInAdmin)
                .orElse(Collections.emptyMap());
    }

    private Map<String, MetricsConsumer> consumersInAdmin(Admin admin) {
        Metrics metrics = admin.getUserMetrics();
        return metrics.getConsumers();
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

    private String getSystemName() {
        Monitoring monitoring = getMonitoringService();
        if (monitoring != null && ! monitoring.getClustername().equals(""))
            return monitoring.getClustername();
        return DEFAULT_NAME_IN_MONITORING_SYSTEM;
    }

    private int getIntervalMinutes() {
        Monitoring monitoring = getMonitoringService();
        if (monitoring != null && monitoring.getInterval() != null) {
            return monitoring.getInterval();
        }
        return DEFAULT_MONITORING_INTERVAL;
    }

    private void  addMetricsProxyComponent(Class<?> componentClass) {
        addSimpleComponent(componentClass.getName(), null, METRICS_PROXY_BUNDLE_NAME);
    }

    private Map<String, String> applicationDimensions() {
        Map<String, String> dimensions = new LinkedHashMap<>();
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
