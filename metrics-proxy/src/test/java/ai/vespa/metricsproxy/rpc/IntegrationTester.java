// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.rpc;

import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.core.ConsumersConfig.Consumer;
import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.core.MonitoringConfig;
import ai.vespa.metricsproxy.core.VespaMetrics;
import ai.vespa.metricsproxy.metric.ExternalMetrics;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensionsConfig;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensionsConfig;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import ai.vespa.metricsproxy.service.HttpMetricFetcher;
import ai.vespa.metricsproxy.service.MockHttpServer;
import ai.vespa.metricsproxy.service.VespaServices;
import ai.vespa.metricsproxy.service.VespaServicesConfig;
import ai.vespa.metricsproxy.service.VespaServicesConfig.Service;

import java.io.IOException;

import static ai.vespa.metricsproxy.core.VespaMetrics.vespaMetricsConsumerId;
import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static ai.vespa.metricsproxy.service.HttpMetricFetcher.STATE_PATH;


/**
 * Setup and shutdown of config and servers for integration-style unit tests.
 *
 * @author hmusum
 * @author gjoranv
 */
public class IntegrationTester implements  AutoCloseable {

    static final String MONITORING_SYSTEM = "test-system";
    static final ConsumerId CUSTOM_CONSUMER_ID = toConsumerId("custom-consumer");
    static final String SERVICE_1_CONFIG_ID = "container/default.0";
    static final String SERVICE_2_CONFIG_ID = "storage/cluster.storage/storage/0";

    private final RpcConnector connector;
    private final MockHttpServer mockHttpServer;
    private final VespaServices vespaServices;

    static {
        HttpMetricFetcher.CONNECTION_TIMEOUT = 60000; // 60 secs in unit tests
    }

    IntegrationTester() {
        try {
            mockHttpServer = new MockHttpServer(null, STATE_PATH);
        } catch (IOException e) {
            throw new RuntimeException("Unable to start web server");
        }

        vespaServices = new VespaServices(servicesConfig(), monitoringConfig(), null);
        MetricsConsumers consumers = new MetricsConsumers(consumersConfig());
        VespaMetrics vespaMetrics = new VespaMetrics(consumers);
        ExternalMetrics externalMetrics = new ExternalMetrics(consumers);
        ApplicationDimensions appDimensions = new ApplicationDimensions(applicationDimensionsConfig());
        NodeDimensions nodeDimensions = new NodeDimensions(nodeDimensionsConfig());

        connector = new RpcConnector(rpcConnectorConfig());
        RpcServer server = new RpcServer(connector, vespaServices,
                new MetricsManager(vespaServices, vespaMetrics, externalMetrics, appDimensions, nodeDimensions));
    }

    MockHttpServer httpServer() {
        return mockHttpServer;
    }

    VespaServices vespaServices() { return vespaServices; }

    @Override
    public void close() {
        mockHttpServer.close();
        this.connector.stop();
    }

    private RpcConnectorConfig rpcConnectorConfig() {
        return new RpcConnectorConfig.Builder()
                .port(0)
                .build();
    }

    private ConsumersConfig consumersConfig() {
        return new ConsumersConfig.Builder()
                .consumer(createConsumer(vespaMetricsConsumerId, "foo.count", "foo_count"))
                .consumer(createConsumer(CUSTOM_CONSUMER_ID, "foo.count", "foo.count"))
                .build();
    }

    private static Consumer.Builder createConsumer(ConsumerId consumerId, String metricName, String outputName) {
        return new Consumer.Builder()
                .name(consumerId.id)
                .metric(new Consumer.Metric.Builder()
                                .dimension(new Consumer.Metric.Dimension.Builder().key("somekey").value("somevalue"))
                                .name(metricName)
                                .outputname(outputName));
    }

    private VespaServicesConfig servicesConfig() {
        return new VespaServicesConfig.Builder()
                .service(createService(toServiceId("container"), SERVICE_1_CONFIG_ID, httpPort()))
                .service(createService(toServiceId("storagenode"), SERVICE_2_CONFIG_ID, httpPort()))
                .build();
    }

    private static Service.Builder createService(ServiceId serviceId, String configId, int port) {
        return new Service.Builder()
                .name(serviceId.id)
                .configId(configId)
                .port(port)
                .dimension(new Service.Dimension.Builder().key("serviceDim").value("serviceDimValue"));
    }

    private MonitoringConfig monitoringConfig() {
        return new MonitoringConfig.Builder()
                .systemName(MONITORING_SYSTEM)
                .build();
    }

    private ApplicationDimensionsConfig applicationDimensionsConfig() {
        return new ApplicationDimensionsConfig.Builder().build();
    }

    private NodeDimensionsConfig nodeDimensionsConfig() {
        return new NodeDimensionsConfig.Builder().build();
    }

    public int rpcPort() {
        return connector.port();
    }

    public int httpPort() {
        return mockHttpServer.port();
    }

}
