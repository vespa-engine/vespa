/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensionsConfig;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.AppDimensionNames;
import com.yahoo.vespa.model.admin.monitoring.Metric;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster.zoneString;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.MY_APPLICATION;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.MY_INSTANCE;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.MY_TENANT;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.checkMetric;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getApplicationDimensionsConfig;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getConsumersConfig;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getCustomConsumer;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getHostedModel;
import static com.yahoo.vespa.model.admin.monitoring.DefaultMetricsConsumer.VESPA_CONSUMER_ID;
import static com.yahoo.vespa.model.admin.monitoring.DefaultVespaMetrics.defaultVespaMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.NetworkMetrics.networkMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.SystemMetrics.systemMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.VespaMetricSet.vespaMetricSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class MetricsProxyContainerClusterTest {

    private static int numDefaultVespaMetrics = defaultVespaMetricSet.getMetrics().size();
    private static int numVespaMetrics = vespaMetricSet.getMetrics().size();
    private static int numSystemMetrics = systemMetricSet.getMetrics().size();
    private static int numNetworkMetrics = networkMetricSet.getMetrics().size();
    private static int numMetricsForDefaultConsumer = numVespaMetrics + numSystemMetrics + numNetworkMetrics;

    @Rule
    public ExpectedException thrown = ExpectedException.none();


    @Test
    public void default_consumer_is_always_present_and_has_all_vespa_metrics_and_all_system_metrics() {
        ConsumersConfig consumersConfig = getConsumersConfig(servicesWithAdminOnly());
        assertEquals(consumersConfig.consumer(0).name(), VESPA_CONSUMER_ID);
        assertEquals(numMetricsForDefaultConsumer, consumersConfig.consumer(0).metric().size());
    }

    @Test
    public void vespa_is_a_reserved_consumer_id() {
        String services = String.join("\n",
                "<services>",
                "    <admin version='2.0'>",
                "        <adminserver hostalias='node1'/>",
                "        <metrics>",
                "            <consumer id='vespa'/>",
                "        </metrics>",
                "    </admin>",
                "</services>"
        );
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("'Vespa' is not allowed as metrics consumer id");
        getConsumersConfig(services);
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
        ConsumersConfig config = getConsumersConfig(services);
        assertEquals(1, config.consumer().size());

        // All default metrics are retained
        ConsumersConfig.Consumer vespaConsumer = config.consumer(0);
        assertEquals(numMetricsForDefaultConsumer + 1, vespaConsumer.metric().size());

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
        getConsumersConfig(services);
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
        VespaModel hostedModel = getHostedModel(servicesWithAdminOnly());
        ApplicationDimensionsConfig config = getApplicationDimensionsConfig(hostedModel);

        assertEquals(zoneString(Zone.defaultZone()), config.dimensions(AppDimensionNames.ZONE));
        assertEquals(MY_TENANT, config.dimensions(AppDimensionNames.TENANT));
        assertEquals(MY_APPLICATION, config.dimensions(AppDimensionNames.APPLICATION));
        assertEquals(MY_INSTANCE, config.dimensions(AppDimensionNames.INSTANCE));
        assertEquals(MY_TENANT + "." + MY_APPLICATION + "." + MY_INSTANCE, config.dimensions(AppDimensionNames.APPLICATION_ID));
        assertEquals(MY_APPLICATION + "." + MY_INSTANCE, config.dimensions(AppDimensionNames.LEGACY_APPLICATION));
    }


    private String servicesWithAdminOnly() {
        return String.join("\n", "<services>",
                           "    <admin version='4.0'>",
                           "        <adminserver hostalias='node1'/>",
                           "    </admin>",
                           "</services>"
        );
    }

}
