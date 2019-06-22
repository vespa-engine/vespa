/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensionsConfig;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.BundlesConfig;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames;
import com.yahoo.vespa.model.admin.monitoring.Metric;
import com.yahoo.vespa.model.admin.monitoring.MetricSet;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

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
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getModel;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getQrStartConfig;
import static com.yahoo.vespa.model.admin.monitoring.DefaultPublicConsumer.DEFAULT_PUBLIC_CONSUMER_ID;
import static com.yahoo.vespa.model.admin.monitoring.DefaultPublicMetrics.defaultPublicMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.VespaMetricsConsumer.VESPA_CONSUMER_ID;
import static com.yahoo.vespa.model.admin.monitoring.DefaultVespaMetrics.defaultVespaMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.NetworkMetrics.networkMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.SystemMetrics.systemMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.VespaMetricSet.vespaMetricSet;
import static java.util.Collections.singleton;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    @Test
    public void verbose_gc_logging_is_disabled() {
        VespaModel model = getModel(servicesWithAdminOnly(), self_hosted);
        QrStartConfig config = getQrStartConfig(model);
        assertFalse(config.jvm().verbosegc());
    }


    @Test
    public void default_public_consumer_is_set_up_for_self_hosted() {
        ConsumersConfig config = consumersConfigFromXml(servicesWithAdminOnly(), self_hosted);
        assertEquals(2, config.consumer().size());
        assertEquals(config.consumer(1).name(), DEFAULT_PUBLIC_CONSUMER_ID);

        int numMetricsForPublicDefaultConsumer = defaultPublicMetricSet.getMetrics().size() + numDefaultVespaMetrics + numSystemMetrics;
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
    public void public_is_a_reserved_consumer_id() {
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

        assertEquals(zoneString(Zone.defaultZone()), config.dimensions(AppDimensionNames.ZONE));
        assertEquals(MY_TENANT, config.dimensions(AppDimensionNames.TENANT));
        assertEquals(MY_APPLICATION, config.dimensions(AppDimensionNames.APPLICATION));
        assertEquals(MY_INSTANCE, config.dimensions(AppDimensionNames.INSTANCE));
        assertEquals(MY_TENANT + "." + MY_APPLICATION + "." + MY_INSTANCE, config.dimensions(AppDimensionNames.APPLICATION_ID));
        assertEquals(MY_APPLICATION + "." + MY_INSTANCE, config.dimensions(AppDimensionNames.LEGACY_APPLICATION));
    }


    private static String servicesWithAdminOnly() {
        return String.join("\n", "<services>",
                           "    <admin version='4.0'>",
                           "        <adminserver hostalias='node1'/>",
                           "    </admin>",
                           "</services>"
        );
    }

}
