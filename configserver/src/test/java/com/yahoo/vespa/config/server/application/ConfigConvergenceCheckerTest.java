// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.yahoo.component.Version;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Consumer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.yahoo.test.json.JsonTestHelper.assertJsonEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * @author Ulf Lilleengen
 * @author mpolden
 */
public class ConfigConvergenceCheckerTest {

    private static final Duration clientTimeout = Duration.ofSeconds(10);
    private final TenantName tenant = TenantName.from("mytenant");
    private final ApplicationId appId = ApplicationId.from(tenant, ApplicationName.from("myapp"), InstanceName.from("myinstance"));

    private Application application;
    private ConfigConvergenceChecker checker;
    private URI service;
    private URI service2;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public final WireMockRule wireMock = new WireMockRule(options().dynamicPort(), true);
    @Rule
    public final WireMockRule wireMock2 = new WireMockRule(options().dynamicPort(), true);

    @Before
    public void setup() {
        service = testServer();
        service2 = testServer(wireMock2);
        Model mockModel = MockModel.createContainer(service.getHost(), service.getPort());
        application = new Application(mockModel,
                                      new ServerCache(),
                                      3,
                                      new Version(0, 0, 0),
                                      MetricUpdater.createTestUpdater(), appId);
        checker = new ConfigConvergenceChecker();
    }

    @Test
    public void service_convergence() {
        { // Known service
            String serviceName = hostAndPort(this.service);
            URI requestUrl = testServer().resolve("/serviceconverge/" + serviceName);
            wireMock.stubFor(get(urlEqualTo("/state/v1/config")).willReturn(okJson("{\"config\":{\"generation\":3}}")));
            HttpResponse serviceResponse = checker.getServiceConfigGenerationResponse(application, hostAndPort(this.service), requestUrl, clientTimeout);
            assertResponse("{\n" +
                           "  \"url\": \"" + requestUrl.toString() + "\",\n" +
                           "  \"host\": \"" + hostAndPort(this.service) + "\",\n" +
                           "  \"wantedGeneration\": 3,\n" +
                           "  \"converged\": true,\n" +
                           "  \"currentGeneration\": 3\n" +
                           "}",
                           200,
                           serviceResponse);
        }

        { // Missing service
            String serviceName = "notPresent:1337";
            URI requestUrl = testServer().resolve("/serviceconverge/" + serviceName);
            HttpResponse response = checker.getServiceConfigGenerationResponse(application, "notPresent:1337", requestUrl, clientTimeout);
            assertResponse("{\n" +
                           "  \"url\": \"" + requestUrl.toString() + "\",\n" +
                           "  \"host\": \"" + serviceName + "\",\n" +
                           "  \"wantedGeneration\": 3,\n" +
                           "  \"problem\": \"Host:port (service) no longer part of application, refetch list of services.\"\n" +
                           "}",
                           410,
                           response);
        }
    }

    @Test
    public void service_list_convergence() {
        {
            String serviceName = hostAndPort(this.service);
            URI requestUrl = testServer().resolve("/serviceconverge");
            URI serviceUrl = testServer().resolve("/serviceconverge/" + serviceName);
            wireMock.stubFor(get(urlEqualTo("/state/v1/config")).willReturn(okJson("{\"config\":{\"generation\":3}}")));
            HttpResponse response = checker.getServiceConfigGenerationsResponse(application, requestUrl, clientTimeout);
            assertResponse("{\n" +
                           "  \"services\": [\n" +
                           "    {\n" +
                           "      \"host\": \"" + serviceUrl.getHost() + "\",\n" +
                           "      \"port\": " + serviceUrl.getPort() + ",\n" +
                           "      \"type\": \"container\",\n" +
                           "      \"url\": \"" + serviceUrl.toString() + "\",\n" +
                           "      \"currentGeneration\":" + 3 + "\n" +
                           "    }\n" +
                           "  ],\n" +
                           "  \"url\": \"" + requestUrl.toString() + "\",\n" +
                           "  \"currentGeneration\": 3,\n" +
                           "  \"wantedGeneration\": 3,\n" +
                           "  \"converged\": true\n" +
                           "}",
                           200,
                           response);
        }


        { // Model with two hosts on different generations
            MockModel model = new MockModel(Arrays.asList(
                    MockModel.createContainerHost(service.getHost(), service.getPort()),
                    MockModel.createContainerHost(service2.getHost(), service2.getPort()))
            );
            Application application = new Application(model, new ServerCache(), 4,
                                                      new Version(0, 0, 0),
                                                      MetricUpdater.createTestUpdater(), appId);

            wireMock.stubFor(get(urlEqualTo("/state/v1/config")).willReturn(okJson("{\"config\":{\"generation\":4}}")));
            wireMock2.stubFor(get(urlEqualTo("/state/v1/config")).willReturn(okJson("{\"config\":{\"generation\":3}}")));

            URI requestUrl = testServer().resolve("/serviceconverge");
            URI serviceUrl = testServer().resolve("/serviceconverge/" + hostAndPort(service));
            URI serviceUrl2 = testServer().resolve("/serviceconverge/" + hostAndPort(service2));
            HttpResponse response = checker.getServiceConfigGenerationsResponse(application, requestUrl, clientTimeout);
            assertResponse("{\n" +
                           "  \"services\": [\n" +
                           "    {\n" +
                           "      \"host\": \"" + service.getHost() + "\",\n" +
                           "      \"port\": " + service.getPort() + ",\n" +
                           "      \"type\": \"container\",\n" +
                           "      \"url\": \"" + serviceUrl.toString() + "\",\n" +
                           "      \"currentGeneration\":" + 4 + "\n" +
                           "    },\n" +
                           "    {\n" +
                           "      \"host\": \"" + service2.getHost() + "\",\n" +
                           "      \"port\": " + service2.getPort() + ",\n" +
                           "      \"type\": \"container\",\n" +
                           "      \"url\": \"" + serviceUrl2.toString() + "\",\n" +
                           "      \"currentGeneration\":" + 3 + "\n" +
                           "    }\n" +
                           "  ],\n" +
                           "  \"url\": \"" + requestUrl.toString() + "\",\n" +
                           "  \"currentGeneration\": 3,\n" +
                           "  \"wantedGeneration\": 4,\n" +
                           "  \"converged\": false\n" +
                           "}",
                           200,
                           response);
        }
    }

    @Test
    public void service_convergence_timeout() {
        URI requestUrl = testServer().resolve("/serviceconverge");
        wireMock.stubFor(get(urlEqualTo("/state/v1/config")).willReturn(aResponse()
                                                                                .withFixedDelay((int) clientTimeout.plus(Duration.ofSeconds(1)).toMillis())
                                                                                .withBody("response too slow")));
        HttpResponse response = checker.getServiceConfigGenerationResponse(application, hostAndPort(service), requestUrl, Duration.ofMillis(1));
        // Message contained in a SocketTimeoutException may differ across platforms, so we do a partial match of the response here
        assertResponse(
                responseBody ->
                        assertThat(responseBody)
                                .startsWith("{\"url\":\"" + requestUrl.toString() + "\",\"host\":\"" + hostAndPort(requestUrl) +
                                        "\",\"wantedGeneration\":3,\"error\":\"")
                                .contains("java.net.SocketTimeoutException: 1 MILLISECONDS")
                                .endsWith("\"}"),
                404,
                response);
    }

    private URI testServer() {
        return testServer(wireMock);
    }

    private URI testServer(WireMockRule wireMock) {
        return URI.create("http://127.0.0.1:" + wireMock.port());
    }

    private static String hostAndPort(URI uri) {
        return uri.getHost() + ":" + uri.getPort();
    }

    private static void assertResponse(String expectedJson, int status, HttpResponse response) {
        assertResponse((responseBody) -> assertJsonEquals(new String(responseBody.getBytes()), expectedJson), status, response);
    }

    private static void assertResponse(Consumer<String> assertFunc, int status, HttpResponse response) {
        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        try {
            response.render(responseBody);
            assertFunc.accept(responseBody.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals(status, response.getStatus());
    }

}
