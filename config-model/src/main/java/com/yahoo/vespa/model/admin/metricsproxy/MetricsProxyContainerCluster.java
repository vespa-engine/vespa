// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.core.MonitoringConfig;
import ai.vespa.metricsproxy.core.VespaMetrics;
import ai.vespa.metricsproxy.http.application.ApplicationMetricsHandler;
import ai.vespa.metricsproxy.http.application.ApplicationMetricsRetriever;
import ai.vespa.metricsproxy.http.application.MetricsNodesConfig;
import ai.vespa.metricsproxy.http.metrics.MetricsV1Handler;
import ai.vespa.metricsproxy.http.prometheus.PrometheusHandler;
import ai.vespa.metricsproxy.http.yamas.YamasHandler;
import ai.vespa.metricsproxy.metric.ExternalMetrics;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensionsConfig;
import ai.vespa.metricsproxy.metric.dimensions.PublicDimensions;
import ai.vespa.metricsproxy.rpc.RpcServer;
import ai.vespa.metricsproxy.service.ConfigSentinelClient;
import ai.vespa.metricsproxy.service.SystemPollerProvider;
import ai.vespa.metricsproxy.telegraf.Telegraf;
import ai.vespa.metricsproxy.telegraf.TelegrafConfig;
import ai.vespa.metricsproxy.telegraf.TelegrafRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.monitoring.MetricSet;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.PlatformBundles;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.model.admin.metricsproxy.ConsumersConfigGenerator.addMetrics;
import static com.yahoo.vespa.model.admin.metricsproxy.ConsumersConfigGenerator.generateConsumers;
import static com.yahoo.vespa.model.admin.metricsproxy.ConsumersConfigGenerator.toConsumerBuilder;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.APPLICATION;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.INSTANCE;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.LEGACY_APPLICATION;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.SYSTEM;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames.TENANT;
import static com.yahoo.vespa.model.admin.monitoring.MetricSet.empty;

/**
 * Container cluster for metrics proxy containers.
 *
 * @author gjoranv
 */
public class MetricsProxyContainerCluster extends ContainerCluster<MetricsProxyContainer> implements
        ApplicationDimensionsConfig.Producer,
        ConsumersConfig.Producer,
        MonitoringConfig.Producer,
        TelegrafConfig.Producer,
        MetricsNodesConfig.Producer
{
    public static final Logger log = Logger.getLogger(MetricsProxyContainerCluster.class.getName());

    private static final String METRICS_PROXY_NAME = "metrics-proxy";
    static final Path METRICS_PROXY_BUNDLE_FILE = PlatformBundles.absoluteBundlePath(METRICS_PROXY_NAME);
    static final String METRICS_PROXY_BUNDLE_NAME = "com.yahoo.vespa." + METRICS_PROXY_NAME;

    private static final Set<Path> UNNECESSARY_BUNDLES = Stream.concat
            (
                    PlatformBundles.VESPA_SECURITY_BUNDLES.stream(),
                    PlatformBundles.VESPA_ZK_BUNDLES.stream()
            ).collect(Collectors.toSet());

    static final class AppDimensionNames {
        static final String SYSTEM = "system";
        static final String TENANT = "tenantName";
        static final String APPLICATION = "applicationName";
        static final String INSTANCE = "instanceName";
        static final String LEGACY_APPLICATION = "app";        // app.instance
    }

    private final TreeConfigProducer<?> parent;
    private final ApplicationId applicationId;

    public MetricsProxyContainerCluster(TreeConfigProducer<?> parent, String name, DeployState deployState) {
        super(parent, name, name, deployState, true);
        this.parent = parent;
        applicationId = deployState.getProperties().applicationId();

        setRpcServerEnabled(true);
        addDefaultHandlersExceptStatus();

        addPlatformBundle(METRICS_PROXY_BUNDLE_FILE);
        addClusterComponents();
    }

    @Override
    protected Set<Path> unnecessaryPlatformBundles() { return UNNECESSARY_BUNDLES; }

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

        addHttpHandler(ApplicationMetricsHandler.class, ApplicationMetricsHandler.METRICS_V1_PATH);
        addMetricsProxyComponent(ApplicationMetricsRetriever.class);

        addTelegrafComponents();
    }

    private void addHttpHandler(Class<? extends ThreadedHttpRequestHandler> clazz, String bindingPath) {
        Handler metricsHandler = createMetricsHandler(clazz, bindingPath);
        addComponent(metricsHandler);
    }

    static Handler createMetricsHandler(Class<? extends ThreadedHttpRequestHandler> clazz, String bindingPath) {
        Handler metricsHandler = new Handler(
                new ComponentModel(clazz.getName(), null, METRICS_PROXY_BUNDLE_NAME, null));
        metricsHandler.addServerBindings(
                SystemBindingPattern.fromHttpPath(bindingPath),
                SystemBindingPattern.fromHttpPath(bindingPath + "/*"));
        return metricsHandler;
    }

    private void addTelegrafComponents() {
        getAdmin().ifPresent(admin -> {
            if (admin.getUserMetrics().usesExternalMetricSystems()) {
                addMetricsProxyComponent(Telegraf.class);
                addMetricsProxyComponent(TelegrafRegistry.class);
            }
        });
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
        var amendedVespaConsumer = addMetrics(MetricsConsumer.vespa, getAdditionalDefaultMetrics().getMetrics());
        builder.consumer.addAll(generateConsumers(amendedVespaConsumer, getUserMetricsConsumers(), getZone().system()));

        builder.consumer.add(toConsumerBuilder(MetricsConsumer.defaultConsumer));
    }

    @Override
    public void getConfig(ApplicationDimensionsConfig.Builder builder) {
        if (isHostedVespa()) {
            builder.dimensions(applicationDimensions());
        }
    }

    @Override
    public void getConfig(TelegrafConfig.Builder builder) {
        builder.isHostedVespa(isHostedVespa());

        var userConsumers = getUserMetricsConsumers();
        for (var consumer : userConsumers.values()) {
            for (var cloudWatch : consumer.cloudWatches()) {
                var cloudWatchBuilder  = new TelegrafConfig.CloudWatch.Builder();
                cloudWatchBuilder
                        .region(cloudWatch.region())
                        .namespace(cloudWatch.namespace())
                        .consumer(cloudWatch.consumer());

                cloudWatch.hostedAuth().ifPresent(hostedAuth -> cloudWatchBuilder
                        .accessKeyName(hostedAuth.accessKeyName)
                        .secretKeyName(hostedAuth.secretKeyName));

                cloudWatch.sharedCredentials().ifPresent(sharedCredentials -> {
                                                             cloudWatchBuilder.file(sharedCredentials.file);
                                                             sharedCredentials.profile.ifPresent(cloudWatchBuilder::profile);
                                                         });
                builder.cloudWatch(cloudWatchBuilder);
            }
        }
    }

    protected boolean messageBusEnabled() { return false; }

    private MetricSet getAdditionalDefaultMetrics() {
        return getAdmin()
                .map(Admin::getAdditionalDefaultMetrics)
                .orElse(empty());
    }

    // Returns the metrics consumers from services.xml
    private Map<String, MetricsConsumer> getUserMetricsConsumers() {
        return getAdmin()
                .map(admin -> admin.getUserMetrics().getConsumers())
                .orElse(Collections.emptyMap());
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

    private void addMetricsProxyComponent(Class<?> componentClass) {
        addSimpleComponent(componentClass.getName(), null, METRICS_PROXY_BUNDLE_NAME);
    }

    private Map<String, String> applicationDimensions() {
        Map<String, String> dimensions = new LinkedHashMap<>();
        dimensions.put(SYSTEM, getZone().system().value());
        dimensions.put(PublicDimensions.ZONE, zoneString(getZone()));
        dimensions.put(PublicDimensions.APPLICATION_ID, serializeWithDots(applicationId));
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
