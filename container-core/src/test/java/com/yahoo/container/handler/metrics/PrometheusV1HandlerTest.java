// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.yahoo.container.handler.metrics.MetricsV2Handler.consumerQuery;
import static com.yahoo.container.handler.metrics.MetricsV2HandlerTest.getFileContents;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class PrometheusV1HandlerTest {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private static final String URI_BASE = "http://localhost";

    private static final String V1_URI = URI_BASE + PrometheusV1Handler.V1_PATH;
    private static final String VALUES_URI = URI_BASE + PrometheusV1Handler.VALUES_PATH;

    // Mock applicationmetrics api
    private static final String MOCK_METRICS_PATH = "/node0";

    private static final String TEST_FILE = "application-prometheus.txt";
    private static final String RESPONSE = getFileContents(TEST_FILE);
    private static final String CPU_METRIC = "cpu";
    private static final String REPLACED_CPU_METRIC = "replaced_cpu";
    private static final String CUSTOM_CONSUMER = "custom-consumer";

    private static RequestHandlerTestDriver testDriver;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort());

    @Before
    public void setup() {
        setupWireMock();
        var handler = new PrometheusV1Handler(Executors.newSingleThreadExecutor(),
                new MetricsProxyApiConfig.Builder()
                        .prometheusApiPath(MOCK_METRICS_PATH)
                        .metricsPort(wireMockRule.port())
                        .metricsApiPath("/Not/In/Use")
                        .build());
        testDriver = new RequestHandlerTestDriver(handler);

    }

    private void setupWireMock() {
        wireMockRule.stubFor(get(urlPathEqualTo(MOCK_METRICS_PATH))
                .willReturn(aResponse().withBody(RESPONSE)));

        // Add a slightly different response for a custom consumer.
        String myConsumerResponse = RESPONSE.replaceAll(CPU_METRIC, REPLACED_CPU_METRIC);
        wireMockRule.stubFor(get(urlPathEqualTo(MOCK_METRICS_PATH))
                .withQueryParam("consumer", equalTo(CUSTOM_CONSUMER))
                .willReturn(aResponse().withBody(myConsumerResponse)));
    }

    @Test
    public void v1_response_contains_values_uri() throws Exception {
        String response = testDriver.sendRequest(V1_URI).readAll();
        JsonNode root = jsonMapper.readTree(response);
        assertTrue(root.has("resources"));

        ArrayNode resources = (ArrayNode) root.get("resources");
        assertEquals(1, resources.size());

        JsonNode valuesUri = resources.get(0);
        assertEquals(VALUES_URI, valuesUri.get("url").asText());
    }

    @Ignore
    @Test
    public void visually_inspect_values_response() {
        String response = testDriver.sendRequest(VALUES_URI).readAll();
        System.out.println(response);
    }

    @Test
    public void invalid_path_yields_error_response() throws Exception {
        String response = testDriver.sendRequest(V1_URI + "/invalid").readAll();
        JsonNode root = jsonMapper.readTree(response);
        assertTrue(root.has("error"));
        assertTrue(root.get("error" ).textValue().startsWith("No content"));
    }

    @Test
    public void values_response_is_equal_to_test_file() {
        String response = testDriver.sendRequest(VALUES_URI).readAll();
        assertEquals(RESPONSE, response);
    }

    @Test
    public void consumer_is_propagated_to_metrics_proxy_api() {
        String response = testDriver.sendRequest(VALUES_URI + consumerQuery(CUSTOM_CONSUMER)).readAll();

        assertTrue(response.contains(REPLACED_CPU_METRIC));
    }
}
