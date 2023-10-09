// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.yahoo.config.ConfigInstance.Builder;
import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ApplicationClusterEndpoint.AuthMethod;
import com.yahoo.config.model.api.ApplicationClusterEndpoint.DnsName;
import com.yahoo.config.model.api.ApplicationClusterEndpoint.RoutingMethod;
import com.yahoo.config.model.api.ApplicationClusterEndpoint.Scope;
import com.yahoo.config.model.api.ApplicationClusterInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.config.server.application.ActiveTokenFingerprints.Token;
import com.yahoo.vespa.config.server.modelfactory.ModelResult;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.yahoo.config.model.api.container.ContainerServiceType.CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.LOGSERVER_CONTAINER;
import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class ActiveTokenFingerprintsClientTest {

    @Rule public final WireMockRule server1 = new WireMockRule(options().dynamicPort(), true);
    @Rule public final WireMockRule server2 = new WireMockRule(options().dynamicPort(), true);
    @Rule public final WireMockRule server3 = new WireMockRule(options().dynamicPort(), true);
    @Rule public final WireMockRule server4 = new WireMockRule(options().dynamicPort(), true);

    @Test
    public void verifyMultipleResponsesCombine() throws Exception {
        try (ActiveTokenFingerprintsClient client = new ActiveTokenFingerprintsClient()) {
            ModelResult app = MockModel::new;
            String uriPath = "/data-plane-tokens/v1";
            server1.stubFor(get(urlEqualTo(uriPath)).willReturn(serverError()));
            server2.stubFor(get(urlEqualTo(uriPath)).willReturn(okJson("""
                                                                       { "tokens": [ {"id": "t1", "fingerprints": [ "foo", "bar", "baz" ] } ] }
                                                                       """)));
            server3.stubFor(get(urlEqualTo(uriPath)).willReturn(aResponse().withStatus(503)));
            server4.stubFor(get(urlEqualTo(uriPath)).willReturn(okJson("""
                                                                       { "tokens": [ {"id": "t2", "fingerprints": [ "quu" ] } ] }
                                                                       """)));
            Map<String, List<Token>> expected = Map.of("localhost",
                                                       List.of(new Token("t1", List.of("foo", "bar", "baz"))));
            assertEquals(expected, client.get(app));
        }
    }

    private class MockModel implements Model {

        @Override
        public Collection<HostInfo> getHosts() {
            return List.of(host(server1.port(), "localhost"),
                           host(server2.port(), "localhost"),
                           host(server3.port(), "localhost"),
                           host(server4.port(), "127.0.0.1")); // Should not be included, see application cluster info below.

        }

        private HostInfo host(int port, String host) {
            return new HostInfo(host,
                                List.of(new ServiceInfo("container",
                                                        CONTAINER.serviceName,
                                                        List.of(new PortInfo(port, List.of("http"))),
                                                        Map.of(),
                                                        "myconfigId",
                                                        host),
                                        new ServiceInfo("logserver",
                                                        LOGSERVER_CONTAINER.serviceName,
                                                        List.of(new PortInfo(port, List.of("http"))),
                                                        Map.of(),
                                                        "myconfigId",
                                                        "127.0.0.1"))); // Don't hit this.
        }

        @Override
        public Set<ApplicationClusterInfo> applicationClusterInfo() {
            return Set.of(new ApplicationClusterInfo() {
                @Override public List<ApplicationClusterEndpoint> endpoints() {
                    return List.of(ApplicationClusterEndpoint.builder()
                                                             .dnsName(DnsName.from("foo"))
                                                             .routingMethod(RoutingMethod.exclusive)
                                                             .authMethod(AuthMethod.token)
                                                             .scope(Scope.zone)
                                                             .clusterId("bar")
                                                             .hosts(List.of("localhost"))
                                                             .build());
                }
                @Override public boolean getDeferChangesUntilRestart() { throw new UnsupportedOperationException(); }
                @Override public String name() { throw new UnsupportedOperationException(); }
            });
        }

        @Override public Builder getConfigInstance(ConfigKey<?> configKey, ConfigDefinition configDefinition) { throw new UnsupportedOperationException(); }
        @Override public Set<ConfigKey<?>> allConfigsProduced() { throw new UnsupportedOperationException(); }
        @Override public Set<String> allConfigIds() { throw new UnsupportedOperationException(); }
        @Override public Set<FileReference> fileReferences() { throw new UnsupportedOperationException(); }
        @Override public AllocatedHosts allocatedHosts() { throw new UnsupportedOperationException(); }

    }

}
