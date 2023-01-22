// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.http.metrics.NodeInfoConfig;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensionsConfig;
import ai.vespa.metricsproxy.metric.dimensions.PublicDimensions;
import ai.vespa.metricsproxy.rpc.RpcConnectorConfig;
import ai.vespa.metricsproxy.service.VespaServicesConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.VespaModelTester;
import org.junit.jupiter.api.Test;

import static com.yahoo.config.model.api.container.ContainerServiceType.METRICS_PROXY_CONTAINER;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.CONTAINER_CONFIG_ID;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.self_hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.containerConfigId;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getModel;

import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getNodeDimensionsConfig;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getRpcConnectorConfig;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getVespaServicesConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author gjoranv
 */
public class MetricsProxyContainerTest {

    @Test
    void one_metrics_proxy_container_is_added_to_every_node() {
        var numberOfHosts = 7;
        var tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);

        VespaModel model = tester.createModel(hostedServicesWithManyNodes(), true);
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        for (var host : model.hostSystem().getHosts()) {
            assertNotNull(host.getService(METRICS_PROXY_CONTAINER.serviceName));

            long metricsProxies = host.getServices().stream()
                    .filter(s -> s.getClass().equals(MetricsProxyContainer.class))
                    .count();
            assertEquals(1, metricsProxies);
        }
    }

    @Test
    void one_metrics_proxy_container_is_added_to_every_node_also_when_dedicated_CCC() {
        var numberOfHosts = 7;
        var tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);

        VespaModel model = tester.createModel(hostedServicesWithManyNodes(), true);
        assertEquals(numberOfHosts, model.getRoot().hostSystem().getHosts().size());

        for (var host : model.hostSystem().getHosts()) {
            assertNotNull(host.getService(METRICS_PROXY_CONTAINER.serviceName));

            long metricsProxies = host.getServices().stream()
                    .filter(s -> s.getClass().equals(MetricsProxyContainer.class))
                    .count();
            assertEquals(1, metricsProxies);
        }
    }

    @Test
    void http_server_is_running_on_expected_port() {
        VespaModel model = getModel(hostedServicesWithContent(), self_hosted);
        MetricsProxyContainer container = (MetricsProxyContainer) model.id2producer().get(CONTAINER_CONFIG_ID);
        assertEquals(19092, container.getSearchPort());
        assertEquals(19092, container.getHealthPort());

        assertTrue(container.getPortsMeta().getTagsAt(0).contains("http"));
        assertTrue(container.getPortsMeta().getTagsAt(0).contains("state"));
    }

    @Test
    void metrics_rpc_server_is_running_on_expected_port() {
        VespaModel model = getModel(hostedServicesWithContent(), self_hosted);
        MetricsProxyContainer container = (MetricsProxyContainer) model.id2producer().get(CONTAINER_CONFIG_ID);

        int offset = 3;
        assertEquals(2, container.getPortsMeta().getTagsAt(offset).size());
        assertTrue(container.getPortsMeta().getTagsAt(offset).contains("rpc"));
        assertTrue(container.getPortsMeta().getTagsAt(offset).contains("metrics"));

        RpcConnectorConfig config = getRpcConnectorConfig(model);
        assertEquals(19095, config.port());
    }

    @Test
    void admin_rpc_server_is_running() {
        VespaModel model = getModel(hostedServicesWithContent(), self_hosted);
        MetricsProxyContainer container = (MetricsProxyContainer) model.id2producer().get(CONTAINER_CONFIG_ID);

        int offset = 2;
        assertEquals(2, container.getPortsMeta().getTagsAt(offset).size());
        assertTrue(container.getPortsMeta().getTagsAt(offset).contains("rpc"));
        assertTrue(container.getPortsMeta().getTagsAt(offset).contains("admin"));
    }

    @Test
    void preload_is_empty() {
        VespaModel model = getModel(hostedServicesWithContent(), self_hosted);
        MetricsProxyContainer container = (MetricsProxyContainer) model.id2producer().get(CONTAINER_CONFIG_ID);

        assertEquals("", container.getPreLoad());
    }

    @Test
    void hosted_application_propagates_node_dimensions() {
        String services = hostedServicesWithContent();
        VespaModel hostedModel = getModel(services, hosted);
        assertEquals(4, hostedModel.getHosts().size());
        String configId = containerConfigId(hostedModel, hosted);
        NodeDimensionsConfig config = getNodeDimensionsConfig(hostedModel, configId);

        assertEquals("content", config.dimensions(PublicDimensions.INTERNAL_CLUSTER_TYPE));
        assertEquals("my-content", config.dimensions(PublicDimensions.INTERNAL_CLUSTER_ID));
        assertEquals("default.mock-application.default.prod.default.my-content", config.dimensions(PublicDimensions.DEPLOYMENT_CLUSTER));
    }

    @Test
    void metrics_v2_handler_is_set_up_with_node_info_config() {
        String services = hostedServicesWithContent();
        VespaModel hostedModel = getModel(services, hosted);

        var container = (MetricsProxyContainer) hostedModel.id2producer().get(containerConfigId(hostedModel, hosted));
        var handlers = container.getHandlers().getComponents();

        assertEquals(1, handlers.size());
        var metricsV2Handler = handlers.iterator().next();

        NodeInfoConfig config = hostedModel.getConfig(NodeInfoConfig.class, metricsV2Handler.getConfigId());
        assertTrue(config.role().startsWith("content/my-content/0/"));
        assertTrue(config.hostname().startsWith("node-1-3-50-"));
    }

    @Test
    void vespa_services_config_has_all_services() {
        VespaServicesConfig vespaServicesConfig = getVespaServicesConfig(hostedServicesWithContent());
        assertEquals(9, vespaServicesConfig.service().size());

        for (var service : vespaServicesConfig.service()) {
            if (service.configId().equals("admin/cluster-controllers/0")) {
                assertEquals("container-clustercontroller", service.name(), "Wrong service name");
                assertEquals(1, service.dimension().size());
                assertEquals("clustername", service.dimension(0).key());
                assertEquals("cluster-controllers", service.dimension(0).value());
            }
        }
    }

    @Test
    void vespa_services_config_has_service_dimensions() {
        VespaServicesConfig vespaServicesConfig = getVespaServicesConfig(hostedServicesWithContent());
        for (var service : vespaServicesConfig.service()) {
            if (service.configId().equals("admin/cluster-controllers/0")) {
                assertEquals(1, service.dimension().size());
                assertEquals("clustername", service.dimension(0).key());
                assertEquals("cluster-controllers", service.dimension(0).value());
            }
        }
    }


    private static String hostedServicesWithManyNodes() {
        return String.join("\n",
                           "<services>",
                           "    <container version='1.0' id='foo'>",
                           "        <nodes count='2'/>",
                           "    </container>",
                           "    <content id='my-content' version='1.0'>",
                           "        <redundancy>2</redundancy>" +
                           "        <documents />",
                           "        <nodes count='2'/>",
                           "    </content>",
                           "</services>");
    }

    private static String hostedServicesWithContent() {
        return String.join("\n",
                           "<services>",
                           "    <content version='1.0' id='my-content'>",
                           "        <redundancy>1</redundancy>" +
                           "        <documents />",
                           "        <nodes count='1' />",
                           "    </content>",
                           "</services>"
        );
    }

}
