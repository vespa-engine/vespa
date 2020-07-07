// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.http.application.ApplicationMetricsHandler;
import ai.vespa.metricsproxy.http.application.MetricsNodesConfig;
import ai.vespa.metricsproxy.http.metrics.MetricsV1Handler;
import ai.vespa.metricsproxy.http.prometheus.PrometheusHandler;
import ai.vespa.metricsproxy.http.yamas.YamasHandler;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensionsConfig;
import ai.vespa.metricsproxy.metric.dimensions.PublicDimensions;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.container.di.PlatformBundlesConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.Handler;
import org.junit.Test;

import java.util.Collection;

import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.METRICS_PROXY_BUNDLE_FILE;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.zoneString;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.CLUSTER_CONFIG_ID;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.MY_APPLICATION;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.MY_INSTANCE;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.MY_TENANT;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.self_hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getApplicationDimensionsConfig;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getMetricsNodesConfig;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getModel;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getQrStartConfig;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.servicesWithAdminOnly;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class MetricsProxyContainerClusterTest {

    @Test
    public void metrics_proxy_bundle_is_included_in_bundles_config() {
        VespaModel model = getModel(servicesWithAdminOnly(), self_hosted);
        var builder = new PlatformBundlesConfig.Builder();
        model.getConfig(builder, CLUSTER_CONFIG_ID);
        PlatformBundlesConfig config = builder.build();
        assertEquals(1, config.bundles().size());
        assertThat(config.bundles(0).value(), endsWith(METRICS_PROXY_BUNDLE_FILE.toString()));
    }

    @Test
    public void cluster_is_prepared_so_that_application_metadata_config_is_produced() {
        VespaModel model = getModel(servicesWithAdminOnly(), self_hosted);
        var builder = new ApplicationMetadataConfig.Builder();
        model.getConfig(builder, CLUSTER_CONFIG_ID);
        ApplicationMetadataConfig config = builder.build();
        assertEquals(MockApplicationPackage.APPLICATION_GENERATION, config.generation());
        assertEquals(MockApplicationPackage.APPLICATION_NAME, config.name());
        assertEquals(MockApplicationPackage.DEPLOYED_BY_USER, config.user());
    }

    private void metrics_proxy_has_expected_qr_start_options(MetricsProxyModelTester.TestMode mode) {
        VespaModel model = getModel(servicesWithAdminOnly(), mode);
        QrStartConfig qrStartConfig = getQrStartConfig(model);
        assertEquals(32, qrStartConfig.jvm().minHeapsize());
        assertEquals(512, qrStartConfig.jvm().heapsize());
        assertEquals(0, qrStartConfig.jvm().heapSizeAsPercentageOfPhysicalMemory());
        assertEquals(2, qrStartConfig.jvm().availableProcessors());
        assertFalse(qrStartConfig.jvm().verbosegc());
        assertEquals("-XX:+UseG1GC -XX:MaxTenuringThreshold=15", qrStartConfig.jvm().gcopts());
        assertEquals(512, qrStartConfig.jvm().stacksize());
        assertEquals(0, qrStartConfig.jvm().directMemorySizeCache());
        assertEquals(32, qrStartConfig.jvm().compressedClassSpaceSize());
        assertEquals(75, qrStartConfig.jvm().baseMaxDirectMemorySize());
    }

    @Test
    public void metrics_proxy_has_expected_qr_start_options() {
        metrics_proxy_has_expected_qr_start_options(self_hosted);
        metrics_proxy_has_expected_qr_start_options(hosted);
    }

    @Test
    public void http_handlers_are_set_up() {
        VespaModel model = getModel(servicesWithAdminOnly(), self_hosted);
        Collection<Handler<?>> handlers = model.getAdmin().getMetricsProxyCluster().getHandlers();
        Collection<ComponentSpecification> handlerClasses = handlers.stream().map(Component::getClassId).collect(toList());

        assertThat(handlerClasses, hasItem(ComponentSpecification.fromString(MetricsV1Handler.class.getName())));
        assertThat(handlerClasses, hasItem(ComponentSpecification.fromString(PrometheusHandler.class.getName())));
        assertThat(handlerClasses, hasItem(ComponentSpecification.fromString(YamasHandler.class.getName())));
        assertThat(handlerClasses, hasItem(ComponentSpecification.fromString(ApplicationMetricsHandler.class.getName())));
    }

    @Test
    public void hosted_application_propagates_application_dimensions() {
        VespaModel hostedModel = getModel(servicesWithAdminOnly(), hosted);
        ApplicationDimensionsConfig config = getApplicationDimensionsConfig(hostedModel);

        assertEquals(Zone.defaultZone().system().value(), config.dimensions(AppDimensionNames.SYSTEM));
        assertEquals(zoneString(Zone.defaultZone()), config.dimensions(PublicDimensions.ZONE));
        assertEquals(MY_TENANT, config.dimensions(AppDimensionNames.TENANT));
        assertEquals(MY_APPLICATION, config.dimensions(AppDimensionNames.APPLICATION));
        assertEquals(MY_INSTANCE, config.dimensions(AppDimensionNames.INSTANCE));
        assertEquals(MY_TENANT + "." + MY_APPLICATION + "." + MY_INSTANCE, config.dimensions(PublicDimensions.APPLICATION_ID));
        assertEquals(MY_APPLICATION + "." + MY_INSTANCE, config.dimensions(AppDimensionNames.LEGACY_APPLICATION));
    }

    @Test
    public void all_nodes_are_included_in_metrics_nodes_config() {
        VespaModel hostedModel = getModel(servicesWithTwoNodes(), hosted);
        MetricsNodesConfig config = getMetricsNodesConfig(hostedModel);
        assertEquals(2, config.node().size());
        assertNodeConfig(config.node(0));
        assertNodeConfig(config.node(1));
    }

    private void assertNodeConfig(MetricsNodesConfig.Node node) {
        assertTrue(node.role().startsWith("container/foo/0/"));
        assertTrue(node.hostname().startsWith("node-1-3-9-"));
        assertEquals(MetricsProxyContainer.BASEPORT, node.metricsPort());
        assertEquals(MetricsV1Handler.VALUES_PATH, node.metricsPath());
    }

    private static String servicesWithTwoNodes() {
        return String.join("\n",
                           "<services>",
                           "    <container version='1.0' id='foo'>",
                           "        <nodes count='2'/>",
                           "    </container>",
                           "</services>");
    }

}
