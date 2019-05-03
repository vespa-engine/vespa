/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensionsConfig;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensionsConfig;
import ai.vespa.metricsproxy.service.VespaServicesConfig;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.monitoring.Metric;
import com.yahoo.vespa.model.test.VespaModelTester;

import static com.yahoo.vespa.model.admin.monitoring.DefaultMetricsConsumer.VESPA_CONSUMER_ID;
import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 */
class MetricsProxyModelTester {

    static final String MY_TENANT = "mytenant";
    static final String MY_APPLICATION = "myapp";
    static final String MY_INSTANCE = "myinstance";
    static final String MY_FLAVOR = "myflavor";

    static VespaModel getModel(String servicesXml) {
        var numberOfHosts = 1;
        var tester = new VespaModelTester();
        tester.enableMetricsProxyContainer(true);
        tester.addHosts(numberOfHosts);
        tester.setHosted(false);
        return tester.createModel(servicesXml, true);
    }

    static VespaModel getHostedModel(String servicesXml) {
        var numberOfHosts = 2;
        var tester = new VespaModelTester();
        tester.enableMetricsProxyContainer(true);
        tester.addHosts(flavorFromString(MY_FLAVOR), numberOfHosts);
        tester.setHosted(true);
        tester.setApplicationId(MY_TENANT, MY_APPLICATION, MY_INSTANCE);
        return tester.createModel(servicesXml, true);
    }

    static boolean checkMetric(ConsumersConfig.Consumer consumer, Metric metric) {
        for (ConsumersConfig.Consumer.Metric m : consumer.metric()) {
            if (metric.name.equals(m.name()) && metric.outputName.equals(m.outputname()))
                return true;
        }
        return false;
    }

    static ConsumersConfig.Consumer getCustomConsumer(String servicesXml) {
        ConsumersConfig config = getConsumersConfig(servicesXml);
        assertEquals(2, config.consumer().size());
        for (ConsumersConfig.Consumer consumer : config.consumer()) {
            if (! consumer.name().equals(VESPA_CONSUMER_ID))
                return consumer;
        }
        throw new RuntimeException("Two consumers with the reserved id - this cannot happen.");
    }

    static ConsumersConfig getConsumersConfig(String servicesXml) {
        return getConsumersConfig(getModel(servicesXml));
    }

    private static ConsumersConfig getConsumersConfig(VespaModel model) {
        String configId = "admin/metrics";
        return new ConsumersConfig((ConsumersConfig.Builder) model.getConfig(new ConsumersConfig.Builder(), configId));
    }

    static ApplicationDimensionsConfig getApplicationDimensionsConfig(VespaModel model) {
        String configId = "admin/metrics";
        return new ApplicationDimensionsConfig((ApplicationDimensionsConfig.Builder) model.getConfig(new ApplicationDimensionsConfig.Builder(), configId));
    }

    static NodeDimensionsConfig getNodeDimensionsConfig(VespaModel model) {
        String configId = "admin/metrics/0";  // This config is produced by the container, not the cluster
        return new NodeDimensionsConfig((NodeDimensionsConfig.Builder) model.getConfig(new NodeDimensionsConfig.Builder(), configId));
    }

    static VespaServicesConfig getVespaServicesConfig(String servicesXml) {
        String configId = "admin/metrics/0";  // This config is produced by the container, not the cluster
        VespaModel model = getModel(servicesXml);
        return new VespaServicesConfig((VespaServicesConfig.Builder) model.getConfig(new VespaServicesConfig.Builder(), configId));
    }

    private static Flavor flavorFromString(String name) {
        return new Flavor(new FlavorsConfig.Flavor(new FlavorsConfig.Flavor.Builder().
                name(name)));
    }

}
