// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.metric.dimensions.PublicDimensions;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.json.GenericApplicationModel;
import ai.vespa.metricsproxy.metric.model.json.GenericJsonModel;
import ai.vespa.metricsproxy.metric.model.json.GenericMetrics;
import ai.vespa.metricsproxy.metric.model.json.GenericService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static ai.vespa.metricsproxy.TestUtil.getFileContents;
import static ai.vespa.metricsproxy.http.ValuesFetcher.defaultMetricsConsumerId;
import static ai.vespa.metricsproxy.http.application.ApplicationMetricsHandler.METRICS_V1_PATH;
import static ai.vespa.metricsproxy.http.application.ApplicationMetricsHandler.METRICS_VALUES_PATH;
import static ai.vespa.metricsproxy.http.application.ApplicationMetricsHandler.PROMETHEUS_VALUES_PATH;
import static ai.vespa.metricsproxy.metric.model.json.JacksonUtil.createObjectMapper;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.yahoo.collections.CollectionUtil.first;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author gjoranv
 */
public class ApplicationMetricsHandlerTest {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private static final String HOST = "localhost";
    private static final String URI_BASE = "http://" + HOST;
    private static final String METRICS_V1_URI = URI_BASE + METRICS_V1_PATH;
    private static final String METRICS_VALUES_URI = URI_BASE + METRICS_VALUES_PATH;
    private static final String PROMETHEUS_VALUES_URI = URI_BASE + PROMETHEUS_VALUES_PATH;

    private static final String TEST_FILE = "generic-sample.json";
    private static final String RESPONSE = getFileContents(TEST_FILE);

    private static final String CPU_METRIC = "cpu.util";
    private static final String REPLACED_CPU_METRIC = "replaced_cpu_util";
    private static final String CUSTOM_CONSUMER = "custom-consumer";

    private static final String MOCK_METRICS_PATH = "/node0";

    private static final Pattern PROMETHEUS_REGEX_FORMAT = Pattern.compile("[a-z_]+([{]([A-Za-z_]+=\"[A-Za-z.\\-\\/0.9_]+\",)*[}])?( [0-9E]+(\\.[0-9E]+)?){2}");

    private int port;

    private static RequestHandlerTestDriver testDriver;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort());

    @Before
    public void setup() {
        setupWireMock();

        ApplicationMetricsRetriever applicationMetricsRetriever =
                new ApplicationMetricsRetriever(nodesConfig(MOCK_METRICS_PATH));

        ApplicationMetricsHandler handler = new ApplicationMetricsHandler(Executors.newSingleThreadExecutor(),
                                                                          applicationMetricsRetriever,
                                                                          getMetricsConsumers());
        applicationMetricsRetriever.getMetrics(defaultMetricsConsumerId);
        applicationMetricsRetriever.getMetrics(ConsumerId.toConsumerId(CUSTOM_CONSUMER));
        applicationMetricsRetriever.startPollAndWait();
        testDriver = new RequestHandlerTestDriver(handler);
    }

    private void setupWireMock() {
        port = wireMockRule.port();
        wireMockRule.stubFor(get(urlPathEqualTo(MOCK_METRICS_PATH))
                                     .withQueryParam("consumer", equalTo(defaultMetricsConsumerId.id))
                                     .willReturn(aResponse().withBody(RESPONSE)));

        // Add a slightly different response for a custom consumer.
        String myConsumerResponse = RESPONSE.replaceAll(CPU_METRIC, REPLACED_CPU_METRIC);
        wireMockRule.stubFor(get(urlPathEqualTo(MOCK_METRICS_PATH))
                                     .withQueryParam("consumer", equalTo(CUSTOM_CONSUMER))
                                     .willReturn(aResponse().withBody(myConsumerResponse)));
    }

    @Test
    public void v1_response_contains_values_uri() throws Exception {
        String response = testDriver.sendRequest(METRICS_V1_URI).readAll();
        JsonNode root = jsonMapper.readTree(response);
        assertTrue(root.has("resources"));

        ArrayNode resources = (ArrayNode) root.get("resources");
        assertEquals(2, resources.size());

        JsonNode valuesUrl = resources.get(0);
        assertEquals(METRICS_VALUES_URI, valuesUrl.get("url").textValue());
        JsonNode prometheusUrl = resources.get(1);
        assertEquals(PROMETHEUS_VALUES_URI, prometheusUrl.get("url").textValue());
    }

    @Ignore
    @Test
    public void visually_inspect_values_response_metrics() throws Exception {
        String response = testDriver.sendRequest(METRICS_VALUES_URI).readAll();
        ObjectMapper mapper = createObjectMapper();
        var jsonModel = mapper.readValue(response, GenericApplicationModel.class);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonModel));
    }

    @Ignore
    @Test
    public void visually_inspect_values_response_prometheus() throws Exception {
        String response = testDriver.sendRequest(PROMETHEUS_VALUES_URI).readAll();
        System.out.println(response);
    }

    @Test
    public void response_contains_node() {
        GenericApplicationModel jsonModel = getResponseAsJsonModel(defaultMetricsConsumerId.id);

        assertEquals(1, jsonModel.nodes.size());
        GenericJsonModel nodeModel = jsonModel.nodes.get(0);
        assertEquals(HOST, nodeModel.hostname);
        assertEquals(MOCK_METRICS_PATH, nodeModel.role);
        assertEquals(2, nodeModel.node.metrics.size());
        assertEquals(16.222, nodeModel.node.metrics.get(0).values.get(CPU_METRIC), 0.0001d);
    }

    @Test
    public void prometheus_response_contains_hostname() {
        String response = testDriver.sendRequest(PROMETHEUS_VALUES_URI).readAll();
        Arrays.stream(response.split("\n"))
                .filter(line -> line.contains("{"))
                .forEach(line -> assertTrue(line.contains("hostname")));
    }

    @Test
    public void prometheus_response_obeys_format() {
        String response = testDriver.sendRequest(PROMETHEUS_VALUES_URI).readAll();
        Arrays.stream(response.split("\n"))
                .filter(line -> !line.startsWith("#"))
                .filter(line -> !line.contains("{"))
                .forEach(line -> assertTrue(PROMETHEUS_REGEX_FORMAT.matcher(line).find()));
    }

    @Test
    public void response_contains_services_with_metrics() {
        GenericApplicationModel jsonModel = getResponseAsJsonModel(defaultMetricsConsumerId.id);

        GenericJsonModel nodeModel = jsonModel.nodes.get(0);
        assertEquals(2, nodeModel.services.size());

        GenericService searchnode = nodeModel.services.get(0);
        assertEquals("searchnode", searchnode.name);
        assertEquals(1, searchnode.metrics.size());
        assertEquals(4, searchnode.metrics.get(0).values.get("queries.count"), 0.0001d);
    }

    @Test
    public void metrics_processors_are_applied() {
        GenericApplicationModel jsonModel = getResponseAsJsonModel(defaultMetricsConsumerId.id);

        GenericService searchnode = jsonModel.nodes.get(0).services.get(0);
        Map<String, String> dimensions = searchnode.metrics.get(0).dimensions;
        assertEquals(6, dimensions.size());
        assertEquals("music.default", dimensions.get(PublicDimensions.APPLICATION_ID));
        assertEquals("container/default", dimensions.get(PublicDimensions.CLUSTER_ID));
        assertEquals("us-west", dimensions.get(PublicDimensions.ZONE));
        assertEquals("search/", dimensions.get(PublicDimensions.API));
        assertEquals("music", dimensions.get(PublicDimensions.DOCUMENT_TYPE));
        assertEquals("default0", dimensions.get(PublicDimensions.SERVICE_ID));
        assertFalse(dimensions.containsKey("non-public"));
    }

    @Test
    public void consumer_is_propagated_in_uri_to_retriever() {
        GenericApplicationModel jsonModel = getResponseAsJsonModel(CUSTOM_CONSUMER);
        GenericJsonModel nodeModel = jsonModel.nodes.get(0);
        GenericMetrics nodeMetrics = nodeModel.node.metrics.get(0);
        assertEquals(REPLACED_CPU_METRIC, first(nodeMetrics.values.keySet()));
    }

    @Test
    public void invalid_path_yields_error_response() throws Exception {
        String response = testDriver.sendRequest(METRICS_V1_URI + "/invalid").readAll();
        JsonNode root = jsonMapper.readTree(response);
        assertTrue(root.has("error"));
    }

    private GenericApplicationModel getResponseAsJsonModel(String consumer) {
        String response = testDriver.sendRequest(METRICS_VALUES_URI + "?consumer=" + consumer).readAll();
        try {
            return createObjectMapper().readValue(response, GenericApplicationModel.class);
        } catch (IOException e) {
            fail("Failed to create json model: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private MetricsNodesConfig nodesConfig(String... paths) {
        var nodes = Arrays.stream(paths)
                .map(this::nodeConfig)
                .collect(toList());
        return new MetricsNodesConfig.Builder()
                .node(nodes)
                .build();
    }

    private MetricsNodesConfig.Node.Builder nodeConfig(String path) {
        return new MetricsNodesConfig.Node.Builder()
                .role(path)
                .hostname("localhost")
                .metricsPath(path)
                .metricsPort(port);
    }

    private static MetricsConsumers getMetricsConsumers() {
        return new MetricsConsumers(new ConsumersConfig.Builder()
                                            .consumer(new ConsumersConfig.Consumer.Builder()
                                                              .name(defaultMetricsConsumerId.id))
                                            .consumer(new ConsumersConfig.Consumer.Builder()
                                                              .name(CUSTOM_CONSUMER))
                                            .build());
    }
}
