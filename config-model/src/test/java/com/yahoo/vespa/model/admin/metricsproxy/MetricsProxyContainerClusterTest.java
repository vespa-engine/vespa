// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.http.MetricsHandler;
import ai.vespa.metricsproxy.http.application.ApplicationMetricsHandler;
import ai.vespa.metricsproxy.http.application.MetricsNodesConfig;
import ai.vespa.metricsproxy.http.prometheus.PrometheusHandler;
import ai.vespa.metricsproxy.http.yamas.YamasHandler;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensionsConfig;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.BundlesConfig;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames;
import com.yahoo.vespa.model.admin.monitoring.Metric;
import com.yahoo.vespa.model.admin.monitoring.MetricSet;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.Handler;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Collection;

import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.METRICS_PROXY_BUNDLE_FILE;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.zoneString;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.CLUSTER_CONFIG_ID;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.MY_APPLICATION;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.MY_INSTANCE;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.MY_TENANT;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.self_hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.checkMetric;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.consumersConfigFromModel;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.consumersConfigFromXml;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getApplicationDimensionsConfig;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getCustomConsumer;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getMetricsNodesConfig;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getModel;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getQrStartConfig;
import static com.yahoo.vespa.model.admin.monitoring.DefaultPublicConsumer.DEFAULT_PUBLIC_CONSUMER_ID;
import static com.yahoo.vespa.model.admin.monitoring.DefaultPublicMetrics.defaultPublicMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.DefaultVespaMetrics.defaultVespaMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.NetworkMetrics.networkMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.SystemMetrics.systemMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.VespaMetricSet.vespaMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.VespaMetricsConsumer.VESPA_CONSUMER_ID;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class MetricsProxyContainerClusterTest {

    private static int numDefaultVespaMetrics = defaultVespaMetricSet.getMetrics().size();
    private static int numVespaMetrics = vespaMetricSet.getMetrics().size();
    private static int numSystemMetrics = systemMetricSet.getMetrics().size();
    private static int numNetworkMetrics = networkMetricSet.getMetrics().size();
    private static int numMetricsForVespaConsumer = numVespaMetrics + numSystemMetrics + numNetworkMetrics;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void metrics_proxy_bundle_is_included_in_bundles_config() {
        VespaModel model = getModel(servicesWithAdminOnly(), self_hosted);
        var builder = new BundlesConfig.Builder();
        model.getConfig(builder, CLUSTER_CONFIG_ID);
        BundlesConfig config = builder.build();
        assertEquals(1, config.bundle().size());
        assertThat(config.bundle(0).value(), endsWith(METRICS_PROXY_BUNDLE_FILE.toString()));
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
        assertEquals(false, qrStartConfig.jvm().verbosegc());
        assertEquals("-XX:+UseG1GC -XX:MaxTenuringThreshold=15", qrStartConfig.jvm().gcopts());
        assertEquals(512, qrStartConfig.jvm().stacksize());
        assertEquals(0, qrStartConfig.jvm().directMemorySizeCache());
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

        assertThat(handlerClasses, hasItem(ComponentSpecification.fromString(MetricsHandler.class.getName())));
        assertThat(handlerClasses, hasItem(ComponentSpecification.fromString(PrometheusHandler.class.getName())));
        assertThat(handlerClasses, hasItem(ComponentSpecification.fromString(YamasHandler.class.getName())));
        assertThat(handlerClasses, hasItem(ComponentSpecification.fromString(ApplicationMetricsHandler.class.getName())));
    }

    @Test
    public void default_public_consumer_is_set_up_for_self_hosted() {
        ConsumersConfig config = consumersConfigFromXml(servicesWithAdminOnly(), self_hosted);
        assertEquals(2, config.consumer().size());
        assertEquals(config.consumer(1).name(), DEFAULT_PUBLIC_CONSUMER_ID);

        int numMetricsForPublicDefaultConsumer = defaultPublicMetricSet.getMetrics().size() + numSystemMetrics;
        assertEquals(numMetricsForPublicDefaultConsumer, config.consumer(1).metric().size());
    }

    @Test
    public void default_public_consumer_is_not_set_up_for_hosted() {
        ConsumersConfig config = consumersConfigFromXml(servicesWithAdminOnly(), hosted);
        assertEquals(1, config.consumer().size());
        assertEquals(config.consumer(0).name(), VESPA_CONSUMER_ID);
    }

    @Test
    public void vespa_consumer_is_always_present_and_has_all_vespa_metrics_and_all_system_metrics() {
        ConsumersConfig config = consumersConfigFromXml(servicesWithAdminOnly(), self_hosted);
        assertEquals(config.consumer(0).name(), VESPA_CONSUMER_ID);
        assertEquals(numMetricsForVespaConsumer, config.consumer(0).metric().size());
    }

    @Test
    public void vespa_consumer_can_be_amended_via_admin_object() {
        VespaModel model = getModel(servicesWithAdminOnly(), self_hosted);
        var additionalMetric = new Metric("additional-metric");
        model.getAdmin().setAdditionalDefaultMetrics(new MetricSet("amender-metrics", singleton(additionalMetric)));

        ConsumersConfig config = consumersConfigFromModel(model);
        assertEquals(numMetricsForVespaConsumer + 1, config.consumer(0).metric().size());

        ConsumersConfig.Consumer vespaConsumer = config.consumer(0);
        assertTrue("Did not contain additional metric", checkMetric(vespaConsumer, additionalMetric));
    }

    @Test
    public void vespa_is_a_reserved_consumer_id() {
        assertReservedConsumerId("Vespa");
    }

    @Test
    public void default_is_a_reserved_consumer_id() {
        assertReservedConsumerId("default");
    }

    private void assertReservedConsumerId(String consumerId) {
        String services = String.join("\n",
                                      "<services>",
                                      "    <admin version='2.0'>",
                                      "        <adminserver hostalias='node1'/>",
                                      "        <metrics>",
                                      "            <consumer id='"  + consumerId + "'/>",
                                      "        </metrics>",
                                      "    </admin>",
                                      "</services>"
        );
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("'" + consumerId + "' is not allowed as metrics consumer id");
        consumersConfigFromXml(services, self_hosted);
    }

    @Test
    public void vespa_consumer_id_is_allowed_for_hosted_infrastructure_applications() {
        String services = String.join("\n",
                "<services application-type='hosted-infrastructure'>",
                "    <admin version='4.0'>",
                "        <adminserver hostalias='node1'/>",
                "        <metrics>",
                "            <consumer id='Vespa'>",
                "                <metric id='custom.metric1'/>",
                "            </consumer>",
                "        </metrics>",
                "    </admin>",
                "</services>"
        );
        VespaModel hostedModel = getModel(services, hosted);
        ConsumersConfig config = consumersConfigFromModel(hostedModel);
        assertEquals(1, config.consumer().size());

        // All default metrics are retained
        ConsumersConfig.Consumer vespaConsumer = config.consumer(0);
        assertEquals(numMetricsForVespaConsumer + 1, vespaConsumer.metric().size());

        Metric customMetric1 = new Metric("custom.metric1");
        assertTrue("Did not contain metric: " + customMetric1, checkMetric(vespaConsumer, customMetric1));
    }

    @Test
    public void consumer_id_is_case_insensitive() {
        String services = String.join("\n",
                "<services>",
                "    <admin version='2.0'>",
                "        <adminserver hostalias='node1'/>",
                "        <metrics>",
                "            <consumer id='A'/>",
                "            <consumer id='a'/>",
                "        </metrics>",
                "    </admin>",
                "</services>"
        );
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("'a' is used as id for two metrics consumers");
        consumersConfigFromXml(services, self_hosted);
    }

    @Test
    public void consumer_with_no_metric_set_has_its_own_metrics_plus_system_metrics_plus_default_vespa_metrics() {
        String services = String.join("\n",
                "<services>",
                "    <admin version='2.0'>",
                "        <adminserver hostalias='node1'/>",
                "        <metrics>",
                "            <consumer id='consumer-with-metrics-only'>",
                "                <metric id='custom.metric1'/>",
                "                <metric id='custom.metric2'/>",
                "            </consumer>",
                "        </metrics>",
                "    </admin>",
                "</services>"
        );
        ConsumersConfig.Consumer consumer = getCustomConsumer(services);

        assertEquals(numSystemMetrics + numDefaultVespaMetrics + 2, consumer.metric().size());

        Metric customMetric1 = new Metric("custom.metric1");
        Metric customMetric2 = new Metric("custom.metric2");
        assertTrue("Did not contain metric: " + customMetric1, checkMetric(consumer, customMetric1));
        assertTrue("Did not contain metric: " + customMetric2, checkMetric(consumer, customMetric2));
    }

    @Test
    public void consumer_with_vespa_metric_set_has_all_vespa_metrics_plus_all_system_metrics_plus_its_own() {
        String services = String.join("\n",
                "<services>",
                "    <admin version='2.0'>",
                "        <adminserver hostalias='node1'/>",
                "        <metrics>",
                "            <consumer id='consumer-with-vespa-set'>",
                "                <metric-set id='vespa'/>",
                "                <metric id='my.extra.metric'/>",
                "            </consumer>",
                "        </metrics>",
                "    </admin>",
                "</services>"
        );
        ConsumersConfig.Consumer consumer = getCustomConsumer(services);
        assertEquals(numVespaMetrics + numSystemMetrics + 1, consumer.metric().size());

        Metric customMetric = new Metric("my.extra.metric");
        assertTrue("Did not contain metric: " + customMetric, checkMetric(consumer, customMetric));
    }

    @Test
    public void hosted_application_propagates_application_dimensions() {
        VespaModel hostedModel = getModel(servicesWithAdminOnly(), hosted);
        ApplicationDimensionsConfig config = getApplicationDimensionsConfig(hostedModel);

        assertEquals(Zone.defaultZone().system().value(), config.dimensions(AppDimensionNames.SYSTEM));
        assertEquals(zoneString(Zone.defaultZone()), config.dimensions(AppDimensionNames.ZONE));
        assertEquals(MY_TENANT, config.dimensions(AppDimensionNames.TENANT));
        assertEquals(MY_APPLICATION, config.dimensions(AppDimensionNames.APPLICATION));
        assertEquals(MY_INSTANCE, config.dimensions(AppDimensionNames.INSTANCE));
        assertEquals(MY_TENANT + "." + MY_APPLICATION + "." + MY_INSTANCE, config.dimensions(AppDimensionNames.APPLICATION_ID));
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
        assertTrue(node.nodeId().startsWith("container/foo/0/"));
        assertTrue(node.hostname().startsWith("node-1-3-9-"));
        assertEquals(MetricsProxyContainer.BASEPORT, node.metricsPort());
        assertEquals(MetricsHandler.VALUES_PATH, node.metricsPath());
    }

    private static String servicesWithAdminOnly() {
        return String.join("\n",
                           "<services>",
                           "    <admin version='4.0'>",
                           "        <adminserver hostalias='node1'/>",
                           "    </admin>",
                           "</services>"
        );
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
