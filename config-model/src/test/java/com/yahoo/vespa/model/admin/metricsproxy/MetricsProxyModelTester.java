// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.http.application.MetricsNodesConfig;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensionsConfig;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensionsConfig;
import ai.vespa.metricsproxy.rpc.RpcConnectorConfig;
import ai.vespa.metricsproxy.service.VespaServicesConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.monitoring.Metric;
import com.yahoo.vespa.model.test.VespaModelTester;

import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.self_hosted;
import static com.yahoo.vespa.model.admin.monitoring.DefaultPublicConsumer.DEFAULT_PUBLIC_CONSUMER_ID;
import static com.yahoo.vespa.model.admin.monitoring.VespaMetricsConsumer.VESPA_CONSUMER_ID;

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
        var numberOfHosts = testMode == hosted ? 2 : 1;
        var tester = new VespaModelTester();
        tester.addHosts(numberOfHosts);
        tester.setHosted(testMode == hosted);
        if (testMode == hosted) tester.setApplicationId(MY_TENANT, MY_APPLICATION, MY_INSTANCE);
        return tester.createModel(servicesXml, true);
    }

    static String configId(VespaModel model, MetricsProxyModelTester.TestMode mode) {
        return (mode == hosted)
                ? CLUSTER_CONFIG_ID + "/" + model.getHosts().iterator().next().getHostname()
                : CONTAINER_CONFIG_ID;
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
            if (! consumer.name().equals(VESPA_CONSUMER_ID) && ! consumer.name().equals(DEFAULT_PUBLIC_CONSUMER_ID))
                return consumer;
        }
        throw new RuntimeException("Custom consumer not found!");
    }

    static ConsumersConfig consumersConfigFromXml(String servicesXml, TestMode testMode) {
        return consumersConfigFromModel(getModel(servicesXml, testMode));
    }

    static ConsumersConfig consumersConfigFromModel(VespaModel model) {
        return new ConsumersConfig((ConsumersConfig.Builder) model.getConfig(new ConsumersConfig.Builder(), CLUSTER_CONFIG_ID));
    }

    static MetricsNodesConfig getMetricsNodesConfig(VespaModel model) {
        return new MetricsNodesConfig((MetricsNodesConfig.Builder) model.getConfig(new MetricsNodesConfig.Builder(), CLUSTER_CONFIG_ID));
    }

    static ApplicationDimensionsConfig getApplicationDimensionsConfig(VespaModel model) {
        return new ApplicationDimensionsConfig((ApplicationDimensionsConfig.Builder) model.getConfig(new ApplicationDimensionsConfig.Builder(), CLUSTER_CONFIG_ID));
    }

    static QrStartConfig getQrStartConfig(VespaModel model) {
        return new QrStartConfig((QrStartConfig.Builder) model.getConfig(new QrStartConfig.Builder(), CLUSTER_CONFIG_ID));
    }

    static NodeDimensionsConfig getNodeDimensionsConfig(VespaModel model, String configId) {
        return new NodeDimensionsConfig((NodeDimensionsConfig.Builder) model.getConfig(new NodeDimensionsConfig.Builder(), configId));
    }

    static VespaServicesConfig getVespaServicesConfig(String servicesXml) {
        VespaModel model = getModel(servicesXml, self_hosted);
        return new VespaServicesConfig((VespaServicesConfig.Builder) model.getConfig(new VespaServicesConfig.Builder(), CONTAINER_CONFIG_ID));
    }

    static RpcConnectorConfig getRpcConnectorConfig(VespaModel model) {
        return new RpcConnectorConfig((RpcConnectorConfig.Builder) model.getConfig(new RpcConnectorConfig.Builder(), CONTAINER_CONFIG_ID));
    }

}
