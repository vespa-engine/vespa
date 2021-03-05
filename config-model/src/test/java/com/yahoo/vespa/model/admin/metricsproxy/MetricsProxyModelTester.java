// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.http.application.MetricsNodesConfig;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensionsConfig;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensionsConfig;
import ai.vespa.metricsproxy.rpc.RpcConnectorConfig;
import ai.vespa.metricsproxy.service.VespaServicesConfig;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.monitoring.Metric;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import com.yahoo.vespa.model.test.VespaModelTester;

import java.util.Optional;

import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.self_hosted;

/**
 * @author gjoranv
 */
class MetricsProxyModelTester {

    static final String MY_TENANT = "mytenant";
    static final String MY_APPLICATION = "myapp";
    static final String MY_INSTANCE = "myinstance";

    static final String CLUSTER_CONFIG_ID = "admin/metrics";

    // Used for all configs that are produced by the container, not the cluster.
    static final String CONTAINER_CONFIG_ID = CLUSTER_CONFIG_ID + "/localhost";

    enum TestMode {
        self_hosted,
        hosted
    }

    static VespaModel getModel(String servicesXml, TestMode testMode) {
        return getModel(servicesXml, testMode, new DeployState.Builder());
    }

    static VespaModel getModel(String servicesXml, TestMode testMode, DeployState.Builder builder) {
        var numberOfHosts = testMode == hosted ? 4 : 1;
        var tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        tester.setHosted(testMode == hosted);
        if (testMode == hosted) tester.setApplicationId(MY_TENANT, MY_APPLICATION, MY_INSTANCE);
        return tester.createModel(servicesXml, true, builder);
    }

    static String containerConfigId(VespaModel model, MetricsProxyModelTester.TestMode mode) {
        return (mode == hosted)
                ? CLUSTER_CONFIG_ID + "/" + model.getHosts().iterator().next().getHostname()
                : CONTAINER_CONFIG_ID;
    }

    static String servicesWithAdminOnly() {
        return String.join("\n",
                           "<services>",
                           "    <admin version='4.0'>",
                           "        <adminserver hostalias='node1'/>",
                           "    </admin>",
                           "</services>"
        );
    }

    static boolean checkMetric(ConsumersConfig.Consumer consumer, Metric metric) {
        for (ConsumersConfig.Consumer.Metric m : consumer.metric()) {
            if (metric.name.equals(m.name()) && metric.outputName.equals(m.outputname()))
                return true;
        }
        return false;
    }

    static ConsumersConfig.Consumer getCustomConsumer(String servicesXml) {
        ConsumersConfig config = consumersConfigFromXml(servicesXml, self_hosted);
        for (ConsumersConfig.Consumer consumer : config.consumer()) {
            if (! consumer.name().equals(MetricsConsumer.vespa.id()) &&
                ! consumer.name().equals(MetricsConsumer.defaultConsumer.id()))
                return consumer;
        }
        throw new RuntimeException("Custom consumer not found!");
    }

    static ConsumersConfig consumersConfigFromXml(String servicesXml, TestMode testMode) {
        return consumersConfigFromModel(getModel(servicesXml, testMode));
    }

    static ConsumersConfig consumersConfigFromModel(VespaModel model) {
        return model.getConfig(ConsumersConfig.class, CLUSTER_CONFIG_ID);
    }

    static MetricsNodesConfig getMetricsNodesConfig(VespaModel model) {
        return model.getConfig(MetricsNodesConfig.class, CLUSTER_CONFIG_ID);
    }

    static ApplicationDimensionsConfig getApplicationDimensionsConfig(VespaModel model) {
        return model.getConfig(ApplicationDimensionsConfig.class, CLUSTER_CONFIG_ID);
    }

    static QrStartConfig getQrStartConfig(VespaModel model, String hostname) {
        return model.getConfig(QrStartConfig.class, CLUSTER_CONFIG_ID + "/" + hostname);
    }

    static NodeDimensionsConfig getNodeDimensionsConfig(VespaModel model, String configId) {
        return model.getConfig(NodeDimensionsConfig.class, configId);
    }

    static VespaServicesConfig getVespaServicesConfig(String servicesXml) {
        VespaModel model = getModel(servicesXml, self_hosted);
        return model.getConfig(VespaServicesConfig.class, CONTAINER_CONFIG_ID);
    }

    static RpcConnectorConfig getRpcConnectorConfig(VespaModel model) {
        return model.getConfig(RpcConnectorConfig.class, CONTAINER_CONFIG_ID);
    }

}
