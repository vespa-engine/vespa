// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorException;
import com.yahoo.vespa.hosted.provision.Node;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * @author dybis
 */
public class RunInContainerTest {
    private final Orchestrator orchestrator = ComponentsProviderWithMocks.orchestratorMock;
    private final String parentHostname = "localhost.test.yahoo.com";
    private JDisc container;
    private int port;

    private int findRandomOpenPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Before
    public void startContainer() throws Exception {
        // To test the initial NodeAdminStateUpdater convergence towards RESUME, orchestrator should
        // deny permission to resume for parent host, otherwise it'll converge to RESUME before REST
        // handler comes up
        doThrow(new RuntimeException()).when(orchestrator).resume(parentHostname);
        port = findRandomOpenPort();
        System.out.println("PORT IS " + port);
        container = JDisc.fromServicesXml(createServiceXml(port), Networking.enable);
    }

    @After
    public void after() {
        container.close();
    }

    private boolean doPutCall(String command) throws IOException {
        HttpClient httpclient = HttpClientBuilder.create().build();
        HttpHost target = new HttpHost("localhost", port, "http");
        HttpPut getRequest = new HttpPut("/rest/" + command);
        HttpResponse httpResponse = httpclient.execute(target, getRequest);
        return httpResponse.getStatusLine().getStatusCode() == 200;
    }

    private String doGetInfoCall() throws IOException {
        HttpClient httpclient = HttpClientBuilder.create().build();
        HttpHost target = new HttpHost("localhost", port, "http");
        HttpGet getRequest = new HttpGet("/rest/info");
        HttpResponse httpResponse = httpclient.execute(target, getRequest);
        HttpEntity entity = httpResponse.getEntity();
        StringWriter writer = new StringWriter();
        IOUtils.copy(entity.getContent(), writer, StandardCharsets.UTF_8);
        return writer.toString();
    }

    private void waitForJdiscContainerToServe() throws InterruptedException {
        Instant start = Instant.now();
        while (Instant.now().minusSeconds(120).isBefore(start)) {
            try {
                HttpClient httpclient = HttpClientBuilder.create().build();
                HttpHost target = new HttpHost("localhost", port, "http");
                HttpGet getRequest = new HttpGet("/rest/info");
                HttpResponse httpResponse = httpclient.execute(target, getRequest);
                if (httpResponse.getStatusLine().getStatusCode() != 200) {
                    continue;
                }
                System.out.println("Container started.");
                return;
            } catch (Exception e) {
                Thread.sleep(100);
            }
        }
        throw new RuntimeException("Could not get answer from container.");
    }

    @After
    public void stopContainer() {
        if (container != null) {
            container.close();
        }
    }

    @Test
    public void testGetContainersToRunAPi() throws IOException, InterruptedException {
        doThrow(new OrchestratorException("Cannot suspend because...")).when(orchestrator).suspend(parentHostname);
        when(ComponentsProviderWithMocks.nodeRepositoryMock.getContainersToRun()).thenReturn(Collections.emptyList());
        waitForJdiscContainerToServe();

        assertFalse(doPutCall("resume")); // Initial is false to force convergence
        doNothing().when(orchestrator).resume(parentHostname);
        Thread.sleep(50);
        assertTrue(doPutCall("resume"));

        // No nodes are allocated to this host yet, so freezing should be fine, but orchestrator doesnt allow node-admin suspend
        doThrow(new OrchestratorException("Cannot suspend because..."))
                .when(orchestrator).suspend(parentHostname, Collections.singletonList(parentHostname));
        assertFalse(doPutCall("suspend/node-admin"));

        // Orchestrator changes its mind, allows node-admin to suspend
        doNothing().when(orchestrator).suspend(parentHostname, Collections.singletonList(parentHostname));
        Thread.sleep(50);
        assertTrue(doPutCall("suspend/node-admin")); // Tick loop should've run several times by now, expect to be suspended

        // Lets try to suspend everything now, should be trivial as we have no active containers to stop services at
        assertFalse(doPutCall("suspend"));
        Thread.sleep(50);
        assertTrue(doPutCall("suspend"));

        // Back to resume
        assertFalse(doPutCall("resume"));
        Thread.sleep(50);
        assertTrue(doPutCall("resume"));

        // Lets try the same, but with an active container running on this host
        when(ComponentsProviderWithMocks.nodeRepositoryMock.getContainersToRun()).thenReturn(
                Collections.singletonList(new ContainerNodeSpec.Builder()
                        .hostname("host1.test.yahoo.com")
                        .wantedDockerImage(new DockerImage("dockerImage"))
                        .nodeState(Node.State.active)
                        .nodeType("tenant")
                        .nodeFlavor("docker")
                        .build()));
        doThrow(new OrchestratorException("Cannot suspend because...")).when(orchestrator)
                .suspend("localhost.test.yahoo.com", Arrays.asList("host1.test.yahoo.com", parentHostname));

        // Initially we are denied to suspend because we have to freeze all the node-agents
        assertFalse(doPutCall("suspend/node-admin"));
        Thread.sleep(50);
        // At this point they should be frozen, but Orchestrator doesn't allow to suspend either the container or the node-admin
        assertFalse(doPutCall("suspend/node-admin"));

        doNothing().when(orchestrator)
                .suspend("localhost.test.yahoo.com", Arrays.asList("host1.test.yahoo.com", parentHostname));

        // Orchestrator successfully suspended everything
        Thread.sleep(50);
        assertTrue(doPutCall("suspend/node-admin"));

        // Allow stopping services in active nodes
        doNothing().when(ComponentsProviderWithMocks.dockerOperationsMock)
                .trySuspendNode(eq(new ContainerName("host1")));
        doNothing().when(ComponentsProviderWithMocks.dockerOperationsMock)
                .stopServicesOnNode(eq(new ContainerName("host1")));

        assertFalse(doPutCall("suspend"));
        Thread.sleep(50);
        assertTrue(doPutCall("suspend"));
    }


    private String createServiceXml(int port) {
        return "<services version=\"1.0\">\n" +
                "  <jdisc version=\"1.0\" jetty=\"true\">\n" +
                "    <handler id=\"com.yahoo.vespa.hosted.node.admin.restapi.RestApiHandler\" bundle=\"node-admin\">\n" +
                "      <binding>http://*/rest/*</binding>\n" +
                "    </handler>\n" +
                "    <component id=\"node-admin\" class=\"com.yahoo.vespa.hosted.node.admin.integrationTests.ComponentsProviderWithMocks\" bundle=\"node-admin\"/>\n" +
                "  <http>" +
                "    <server id=\'myServer\' port=\'" + port + "\' />" +
                "  </http>" +
                "  </jdisc>\n" +
                "</services>\n";
    }
}