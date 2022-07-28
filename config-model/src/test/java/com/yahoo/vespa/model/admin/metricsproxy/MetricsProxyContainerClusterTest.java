// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.container.di.config.PlatformBundlesConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames;
import com.yahoo.vespa.model.container.PlatformBundles;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.Handler;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

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
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.servicesWithAdminOnly;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
public class MetricsProxyContainerClusterTest {

    @Test
    void metrics_proxy_bundle_is_included_in_bundles_config() {
        VespaModel model = getModel(servicesWithAdminOnly(), self_hosted);
        PlatformBundlesConfig config = model.getConfig(PlatformBundlesConfig.class, CLUSTER_CONFIG_ID);
        assertTrue(config.bundlePaths().stream()
                .anyMatch(p -> p.equals(METRICS_PROXY_BUNDLE_FILE.toString())));
    }

    @Test
    void unnecessary_bundles_are_not_installed() {
        VespaModel model = getModel(servicesWithAdminOnly(), self_hosted);
        PlatformBundlesConfig config = model.getConfig(PlatformBundlesConfig.class, CLUSTER_CONFIG_ID);

        Set<String> unnecessaryBundles = Stream.concat
                (
                        PlatformBundles.VESPA_SECURITY_BUNDLES.stream(),
                        PlatformBundles.VESPA_ZK_BUNDLES.stream()
                ).map(Path::toString).collect(toSet());

        assertTrue(config.bundlePaths().stream()
                .noneMatch(unnecessaryBundles::contains));
    }

    @Test
    void cluster_is_prepared_so_that_application_metadata_config_is_produced() {
        VespaModel model = getModel(servicesWithAdminOnly(), self_hosted);
        ApplicationMetadataConfig config = model.getConfig(ApplicationMetadataConfig.class, CLUSTER_CONFIG_ID);
        assertEquals(MockApplicationPackage.APPLICATION_GENERATION, config.generation());
        assertEquals(MockApplicationPackage.APPLICATION_NAME, config.name());
    }

    @Test
    void http_handlers_are_set_up() {
        VespaModel model = getModel(servicesWithAdminOnly(), self_hosted);
        Collection<Handler> handlers = model.getAdmin().getMetricsProxyCluster().getHandlers();
        Collection<ComponentSpecification> handlerClasses = handlers.stream().map(Component::getClassId).collect(toList());

        assertTrue(handlerClasses.contains(ComponentSpecification.fromString(MetricsV1Handler.class.getName())));
        assertTrue(handlerClasses.contains(ComponentSpecification.fromString(PrometheusHandler.class.getName())));
        assertTrue(handlerClasses.contains(ComponentSpecification.fromString(YamasHandler.class.getName())));
        assertTrue(handlerClasses.contains(ComponentSpecification.fromString(ApplicationMetricsHandler.class.getName())));
    }

    @Test
    void hosted_application_propagates_application_dimensions() {
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
    void all_nodes_are_included_in_metrics_nodes_config() {
        VespaModel hostedModel = getModel(servicesWithTwoNodes(), hosted);
        MetricsNodesConfig config = getMetricsNodesConfig(hostedModel);
        assertEquals(2, config.node().size());
        assertNodeConfig(config.node(0));
        assertNodeConfig(config.node(1));
    }

    private void assertNodeConfig(MetricsNodesConfig.Node node) {
        assertTrue(node.role().startsWith("container/foo/0/"));
        assertTrue(node.hostname().startsWith("node-1-3-50-"));
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
