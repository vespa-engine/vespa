package com.yahoo.vespa.config.server.application;// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.vespa.config.server.modelfactory.ModelResult;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.yahoo.config.model.api.container.ContainerServiceType.CLUSTERCONTROLLER_CONTAINER;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
public class DefaultClusterReindexingStatusClientTest {

    @Rule public final WireMockRule server1 = new WireMockRule(options().dynamicPort(), true);
    @Rule public final WireMockRule server2 = new WireMockRule(options().dynamicPort(), true);
    @Rule public final WireMockRule server3 = new WireMockRule(options().dynamicPort(), true);

    @Test
    public void combines_result_from_multiple_cluster_controller_clusters() throws IOException {
        var client = new DefaultClusterReindexingStatusClient();
        MockApplication app = new MockApplication();
        String uriPath = "/reindexing/v1/status";
        server1.stubFor(get(urlEqualTo(uriPath)).willReturn(serverError()));
        server2.stubFor(get(urlEqualTo(uriPath)).willReturn(okJson(
                "{" +
                "  \"clusters\": {" +
                "    \"cluster1\": {" +
                "      \"documentTypes\": {" +
                "        \"music\": {" +
                "          \"startedMillis\":0," +
                "          \"state\": \"" + ClusterReindexing.State.RUNNING.asString() + "\"" +
                "        }" +
                "      }" +
                "    }" +
                "  }" +
                "}")));
        server3.stubFor(get(urlEqualTo(uriPath)).willReturn(okJson(
                "{" +
                "  \"clusters\": {" +
                "    \"cluster2\": {" +
                "      \"documentTypes\": {" +
                "        \"artist\": {" +
                "          \"startedMillis\":50," +
                "          \"endedMillis\":150," +
                "          \"progress\": 0.5," +
                "          \"state\": \"" + ClusterReindexing.State.SUCCESSFUL.asString() + "\"," +
                "          \"message\":\"success\"" +
                "        }" +
                "      }" +
                "    }" +
                "  }" +
                "}")));
        Map<String, ClusterReindexing> expected = Map.of("cluster1",
                                                         new ClusterReindexing(Map.of("music",
                                                                                      new ClusterReindexing.Status(Instant.ofEpochMilli(0),
                                                                                                                   null,
                                                                                                                   ClusterReindexing.State.RUNNING,
                                                                                                                   null,
                                                                                                                   null))),
                                                         "cluster2",
                                                         new ClusterReindexing(Map.of("artist",
                                                                                      new ClusterReindexing.Status(Instant.ofEpochMilli(50),
                                                                                                                   Instant.ofEpochMilli(150),
                                                                                                                   ClusterReindexing.State.SUCCESSFUL,
                                                                                                                   "success",
                                                                                                                   0.5))));
        Map<String, ClusterReindexing> result = client.getReindexingStatus(app);
        assertEquals(expected, result);
    }


    private class MockApplication implements ModelResult {
        private final Collection<HostInfo> hosts;

        MockApplication() {
            this.hosts = createHosts();
        }

        @Override
        public Model getModel() {
            Model model = mock(Model.class);
            when(model.getHosts()).thenReturn(hosts);
            return model;
        }

        private Collection<HostInfo> createHosts() {
            return List.of(
                    createHostInfo(server1.port(), "cc1.1", "cluster1"),
                    createHostInfo(server2.port(), "cc1.2", "cluster1"),
                    createHostInfo(server3.port(), "cc2.1", "cluster2"));
        }

        private HostInfo createHostInfo(int serverPort, String serviceName, String clusterId) {
            return new HostInfo(
                    "localhost",
                    List.of(new ServiceInfo(
                            serviceName,
                            CLUSTERCONTROLLER_CONTAINER.serviceName,
                            List.of(new PortInfo(serverPort, List.of("state"))),
                            Map.of("clustername", clusterId),
                            "myconfigId",
                            "localhost")));
        }

    }


}