// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.yahoo.component.Version;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.yahoo.vespa.config.server.application.ConfigConvergenceChecker.ServiceListResponse;
import static com.yahoo.vespa.config.server.application.ConfigConvergenceChecker.ServiceResponse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
            wireMock.stubFor(get(urlEqualTo("/state/v1/config")).willReturn(okJson("{\"config\":{\"generation\":3}}")));

            ServiceResponse response = checker.getServiceConfigGeneration(application, hostAndPort(this.service), clientTimeout);
            assertEquals(3, response.wantedGeneration.longValue());
            assertEquals(3, response.currentGeneration.longValue());
            assertTrue(response.converged);
            assertEquals(ServiceResponse.Status.ok, response.status);
        }

        { // Missing service
            ServiceResponse response = checker.getServiceConfigGeneration(application, "notPresent:1337", clientTimeout);
            assertEquals(3, response.wantedGeneration.longValue());
            assertEquals(ServiceResponse.Status.hostNotFound, response.status);
        }
    }

    @Test
    public void service_list_convergence() {
        {
            wireMock.stubFor(get(urlEqualTo("/state/v1/config")).willReturn(okJson("{\"config\":{\"generation\":3}}")));

            ServiceListResponse response = checker.checkConvergenceForAllServices(application, clientTimeout);
            assertEquals(3, response.wantedGeneration);
            assertEquals(3, response.currentGeneration);
            assertTrue(response.converged);
            List<ServiceListResponse.Service> services = response.services;
            assertEquals(1, services.size());
            assertService(this.service, services.get(0), 3);
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

            ServiceListResponse response = checker.checkConvergenceForAllServices(application, clientTimeout);
            assertEquals(4, response.wantedGeneration);
            assertEquals(3, response.currentGeneration);
            assertFalse(response.converged);

            List<ServiceListResponse.Service> services = response.services;
            assertEquals(2, services.size());
            assertService(this.service, services.get(0), 4);
            assertService(this.service2, services.get(1), 3);
        }
    }


    @Test
    public void service_convergence_timeout() {
        wireMock.stubFor(get(urlEqualTo("/state/v1/config")).willReturn(aResponse()
                                                                                .withFixedDelay((int) clientTimeout.plus(Duration.ofSeconds(1)).toMillis())
                                                                                .withBody("response too slow")));
        ServiceResponse response = checker.getServiceConfigGeneration(application, hostAndPort(service), Duration.ofMillis(1));

        assertEquals(3, response.wantedGeneration.longValue());
        assertEquals(ServiceResponse.Status.notFound, response.status);
        assertTrue(response.errorMessage.get().contains("java.net.SocketTimeoutException: 1 MILLISECONDS"));
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

    private void assertService(URI uri, ServiceListResponse.Service service1, long expectedGeneration) {
        assertEquals(expectedGeneration, service1.currentGeneration.longValue());
        assertEquals(uri.getHost(), service1.serviceInfo.getHostName());
        assertEquals(uri.getPort(), ConfigConvergenceChecker.getStatePort(service1.serviceInfo).get().intValue());
        assertEquals("container", service1.serviceInfo.getServiceType());
    }

}
