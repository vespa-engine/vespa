// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.core.ConsumersConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.monitoring.Metric;
import com.yahoo.vespa.model.admin.monitoring.MetricSet;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import org.junit.jupiter.api.Test;

import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.self_hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.checkMetric;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.consumersConfigFromModel;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.consumersConfigFromXml;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getCustomConsumer;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getModel;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.servicesWithAdminOnly;
import static com.yahoo.vespa.model.admin.monitoring.DefaultMetrics.defaultMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.DefaultVespaMetrics.defaultVespaMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.NetworkMetrics.networkMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.SystemMetrics.systemMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.VespaMetricSet.vespaMetricSet;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MetricsProxyContainerCluster} related to metrics consumers.
 *
 * @author gjoranv
 */
public class MetricsConsumersTest {

    private static final int numPublicDefaultMetrics = defaultMetricSet.getMetrics().size();
    private static final int numDefaultVespaMetrics = defaultVespaMetricSet.getMetrics().size();
    private static final int numVespaMetrics = vespaMetricSet.getMetrics().size();
    private static final int numSystemMetrics = systemMetricSet.getMetrics().size();
    private static final int numNetworkMetrics = networkMetricSet.getMetrics().size();
    private static final int numMetricsForVespaConsumer = numVespaMetrics + numSystemMetrics + numNetworkMetrics;

    @Test
    void default_public_consumer_is_set_up_for_self_hosted() {
        ConsumersConfig config = consumersConfigFromXml(servicesWithAdminOnly(), self_hosted);
        assertEquals(3, config.consumer().size());
        assertEquals(MetricsConsumer.defaultConsumer.id(), config.consumer(2).name());
        int numMetricsForPublicDefaultConsumer = defaultMetricSet.getMetrics().size() + numSystemMetrics;
        assertEquals(numMetricsForPublicDefaultConsumer, config.consumer(2).metric().size());
    }

    @Test
    void consumers_are_set_up_for_hosted() {
        ConsumersConfig config = consumersConfigFromXml(servicesWithAdminOnly(), hosted);
        assertEquals(3, config.consumer().size());
        assertEquals(MetricsConsumer.vespa.id(), config.consumer(0).name());
        assertEquals(MetricsConsumer.autoscaling.id(), config.consumer(1).name());
        assertEquals(MetricsConsumer.defaultConsumer.id(), config.consumer(2).name());
    }

    @Test
    void vespa_consumer_is_always_present_and_has_all_vespa_metrics_and_all_system_metrics() {
        ConsumersConfig config = consumersConfigFromXml(servicesWithAdminOnly(), self_hosted);
        assertEquals(MetricsConsumer.vespa.id(), config.consumer(0).name());
        assertEquals(numMetricsForVespaConsumer, config.consumer(0).metric().size());
    }

    @Test
    void vespa_consumer_can_be_amended_via_admin_object() {
        VespaModel model = getModel(servicesWithAdminOnly(), self_hosted);
        var additionalMetric = new Metric("additional-metric");
        model.getAdmin().setAdditionalDefaultMetrics(new MetricSet("amender-metrics", singleton(additionalMetric)));

        ConsumersConfig config = consumersConfigFromModel(model);
        assertEquals(numMetricsForVespaConsumer + 1, config.consumer(0).metric().size());

        ConsumersConfig.Consumer vespaConsumer = config.consumer(0);
        assertTrue(checkMetric(vespaConsumer, additionalMetric), "Did not contain additional metric");
    }

    @Test
    void vespa_is_a_reserved_consumer_id() {
        assertReservedConsumerId("Vespa");
    }

    @Test
    void default_is_a_reserved_consumer_id() {
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
        try {
            consumersConfigFromXml(services, self_hosted);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("'" + consumerId + "' is not allowed as metrics consumer id (case is ignored.)", e.getMessage());
        }
    }

    @Test
    void vespa_consumer_id_is_allowed_for_hosted_infrastructure_applications() {
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
        assertEquals(3, config.consumer().size());

        // All default metrics are retained
        ConsumersConfig.Consumer vespaConsumer = config.consumer(0);
        assertEquals(numMetricsForVespaConsumer + 1, vespaConsumer.metric().size());

        Metric customMetric1 = new Metric("custom.metric1");
        assertTrue(checkMetric(vespaConsumer, customMetric1), "Did not contain metric: " + customMetric1);
    }

    @Test
    void consumer_id_is_case_insensitive() {
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

        try {
            consumersConfigFromXml(services, self_hosted);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("'a' is used as id for two metrics consumers (case is ignored.)", e.getMessage());
        }
    }

    @Test
    void non_existent_metric_set_causes_exception() {
        String services = String.join("\n",
                "<services>",
                "    <admin version='2.0'>",
                "        <adminserver hostalias='node1'/>",
                "        <metrics>",
                "            <consumer id='consumer-with-non-existent-default-set'>",
                "                <metric-set id='non-existent'/>",
                "            </consumer>",
                "        </metrics>",
                "    </admin>",
                "</services>"
        );
        try {
            consumersConfigFromXml(services, self_hosted);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("No such metric-set: non-existent", e.getMessage());
        }
    }

    @Test
    void consumer_with_no_metric_set_has_its_own_metrics_plus_system_metrics_plus_default_vespa_metrics() {
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
        assertTrue(checkMetric(consumer, customMetric1), "Did not contain metric: " + customMetric1);
        assertTrue(checkMetric(consumer, customMetric2), "Did not contain metric: " + customMetric2);
    }

    @Test
    void consumer_with_default_metric_set_has_all_its_metrics_plus_all_system_metrics_plus_its_own() {
        String services = String.join("\n",
                "<services>",
                "    <admin version='2.0'>",
                "        <adminserver hostalias='node1'/>",
                "        <metrics>",
                "            <consumer id='consumer-with-public-default-set'>",
                "                <metric-set id='default'/>",
                "                <metric id='custom.metric'/>",
                "            </consumer>",
                "        </metrics>",
                "    </admin>",
                "</services>"
        );
        ConsumersConfig.Consumer consumer = getCustomConsumer(services);

        assertEquals(numPublicDefaultMetrics + numSystemMetrics + 1, consumer.metric().size());

        Metric customMetric = new Metric("custom.metric");
        assertTrue(checkMetric(consumer, customMetric), "Did not contain metric: " + customMetric);
    }

    @Test
    void consumer_with_vespa_metric_set_has_all_vespa_metrics_plus_all_system_metrics_plus_its_own() {
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
        assertTrue(checkMetric(consumer, customMetric), "Did not contain metric: " + customMetric);
    }

}
