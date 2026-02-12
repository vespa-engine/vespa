// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.yahoo.component.Version;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ServiceConfigState;
import com.yahoo.config.model.api.ServiceInfo;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author glebashnik
 */
public class ConfigStateCheckerTest {

    private static final Duration clientTimeout = Duration.ofSeconds(10);
    private final TenantName tenant = TenantName.from("mytenant");
    private final ApplicationId appId =
            ApplicationId.from(tenant, ApplicationName.from("myapp"), InstanceName.from("myinstance"));

    private Application application;
    private ConfigStateChecker checker;
    private URI service1;
    private URI service2;
    private URI service3;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public final WireMockRule wireMock1 = new WireMockRule(options().dynamicPort(), true);

    @Rule
    public final WireMockRule wireMock2 = new WireMockRule(options().dynamicPort(), true);

    @Rule
    public final WireMockRule wireMock3 = new WireMockRule(options().dynamicPort(), true);

    @Before
    public void setup() {
        service1 = testServer(wireMock1, "127.0.0.1");
        service2 = testServer(wireMock2, "127.0.0.1");
        service3 = testServer(wireMock3, "localhost");

        Model mockModel = MockModel.createContainer(service1.getHost(), service1.getPort());
        application = new Application(
                mockModel, new ServerCache(), 3, new Version(0, 0, 0), MetricUpdater.createTestUpdater(), appId);
        checker = new ConfigStateChecker();
    }

    @Test
    public void test_getServiceConfigStates_many_services() {
        // Model with two services on 127.0.0.1 and one on localhost
        MockModel model = new MockModel(List.of(
                MockModel.createContainerHost(service1.getHost(), service1.getPort()),
                MockModel.createContainerHost(service2.getHost(), service2.getPort()),
                MockModel.createContainerHost(service3.getHost(), service3.getPort())));
        Application application = new Application(
                model, new ServerCache(), 4, new Version(0, 0, 0), MetricUpdater.createTestUpdater(), appId);

        wireMock1.stubFor(get(urlEqualTo("/state/v1/config"))
                .willReturn(okJson("{\"config\":{\"generation\":4,\"applyOnRestart\":false}}")));
        wireMock2.stubFor(get(urlEqualTo("/state/v1/config"))
                .willReturn(okJson("{\"config\":{\"generation\":3}}")));
        wireMock3.stubFor(get(urlEqualTo("/state/v1/config")).willReturn(okJson("{\"config\":{\"generation\":5}}")));

        // Query only services on 127.0.0.1 (should return 2 services, localhost not included)
        Set<String> hostnames = Set.of("127.0.0.1");
        Map<ServiceInfo, ServiceConfigState> response =
                checker.getServiceConfigStates(application, clientTimeout, hostnames);

        assertEquals(2, response.size());

        List<Map.Entry<ServiceInfo, ServiceConfigState>> entries = List.copyOf(response.entrySet());

        // First service (127.0.0.1)
        ServiceInfo serviceInfo1 = entries.get(0).getKey();
        ServiceConfigState state1 = entries.get(0).getValue();
        assertEquals("127.0.0.1", serviceInfo1.getHostName());
        assertEquals(4, state1.currentGeneration());
        assertTrue(state1.applyOnRestart().isPresent());
        assertFalse(state1.applyOnRestart().get());

        // Second service (127.0.0.1)
        ServiceInfo serviceInfo2 = entries.get(1).getKey();
        ServiceConfigState state2 = entries.get(1).getValue();
        assertEquals("127.0.0.1", serviceInfo2.getHostName());
        assertEquals(3, state2.currentGeneration());
        assertTrue(state2.applyOnRestart().isEmpty());
    }

    @Test
    public void test_getServiceConfigStates_timeout() {
        wireMock1.stubFor(get(urlEqualTo("/state/v1/config"))
                .willReturn(aResponse()
                        .withFixedDelay(
                                (int) clientTimeout.plus(Duration.ofSeconds(1)).toMillis())
                        .withBody("response too slow")));

        Set<String> hostnames = Set.of(service1.getHost());
        Map<ServiceInfo, ServiceConfigState> response =
                checker.getServiceConfigStates(application, Duration.ofMillis(1), hostnames);

        assertEquals(1, response.size());
        ServiceConfigState state = response.values().iterator().next();

        // On timeout, the service should return a state with generation -1
        assertEquals(-1, state.currentGeneration());
        assertFalse(state.applyOnRestart().isPresent());
    }

    @Test
    public void test_getServiceConfigStates_unknown_host() {
        // No stub configured, so the service will not be reachable
        Set<String> hostnames = Set.of("unknown");
        Map<ServiceInfo, ServiceConfigState> response =
                checker.getServiceConfigStates(application, clientTimeout, hostnames);

        // Should return empty map since the hostname doesn't match any service in the application
        assertEquals(0, response.size());
    }

    private URI testServer(WireMockRule wireMock, String hostname) {
        return URI.create("http://" + hostname + ":" + wireMock.port());
    }
}
