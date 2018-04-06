// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.concurrent.classlock.ClassLocking;
import com.yahoo.config.provision.NodeType;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.NodeRepositoryNode;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.AclMaintainer;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdaterImpl;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.OrchestratorException;
import com.yahoo.vespa.hosted.node.admin.provider.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
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
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author dybis
 */
public class RunInContainerTest {
    private final Logger logger = Logger.getLogger("RunInContainerTest");

    private static final NodeRepository nodeRepositoryMock = mock(NodeRepository.class);
    private static final Orchestrator orchestratorMock = mock(Orchestrator.class);
    private static final DockerOperations dockerOperationsMock = mock(DockerOperations.class);

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
        // To test the initial NodeAdminStateUpdaterImpl convergence towards RESUME, orchestrator should
        // deny permission to resume for parent host, otherwise it'll converge to RESUME before REST
        // handler comes up
        doThrow(new RuntimeException()).when(orchestratorMock).resume(parentHostname);
        port = findRandomOpenPort();
        System.out.println("PORT IS " + port);
        logger.info("PORT IS " + port);
        container = JDisc.fromServicesXml(createServiceXml(port), Networking.enable);
    }

    @After
    public void stopContainer() {
        if (container != null) {
            container.close();
        }
    }

    private boolean doPutCall(String command) throws IOException {
        logger.info("info before '"+command+"' is: " + doGetInfoCall());
        HttpClient httpclient = HttpClientBuilder.create().build();
        HttpHost target = new HttpHost("localhost", port, "http");
        HttpPut getRequest = new HttpPut("/rest/" + command);
        HttpResponse httpResponse = httpclient.execute(target, getRequest);
        logger.info("info after '"+command+"' is: " + doGetInfoCall());
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
                logger.info("Container started.");
                System.out.println("Container started.");
                return;
            } catch (Exception e) {
                Thread.sleep(100);
            }
        }
        throw new RuntimeException("Could not get answer from container.");
    }

    @Ignore
    @Test
    public void testGetContainersToRunAPi() throws IOException, InterruptedException {
        doThrow(new OrchestratorException("Cannot suspend because...")).when(orchestratorMock).suspend(parentHostname);
        when(nodeRepositoryMock.getNodes(eq(parentHostname))).thenReturn(Collections.emptyList());
        waitForJdiscContainerToServe();

        assertTrue("The initial resume command should fail because it needs to converge first",
                verifyWithRetries("resume", false));
        doNothing().when(orchestratorMock).resume(parentHostname);
        assertTrue(verifyWithRetries("resume", true));

        doThrow(new OrchestratorException("Cannot suspend because..."))
                .when(orchestratorMock).suspend(parentHostname, Collections.singletonList(parentHostname));
        assertTrue("Should fail because orchestrator does not allow node-admin to suspend",
                verifyWithRetries("suspend/node-admin", false));

        // Orchestrator changes its mind, allows node-admin to suspend
        doNothing().when(orchestratorMock).suspend(parentHostname, Collections.singletonList(parentHostname));
        assertTrue(verifyWithRetries("suspend/node-admin", true));

        // Lets try to suspend everything now, should be trivial as we have no active containers to stop services at
        assertTrue(verifyWithRetries("suspend", false));
        assertTrue(verifyWithRetries("suspend", true));

        // Back to resume
        assertTrue(verifyWithRetries("resume", false));
        assertTrue(verifyWithRetries("resume", true));

        // Lets try the same, but with an active container running on this host
        when(nodeRepositoryMock.getNodes(eq(parentHostname))).thenReturn(
                Collections.singletonList(new NodeRepositoryNode.Builder()
                        .hostname("host1.test.yahoo.com")
                        .wantedDockerImage(new DockerImage("dockerImage"))
                        .nodeState(Node.State.active)
                        .nodeType(NodeType.tenant)
                        .nodeFlavor("docker")
                        .build()));
        doThrow(new OrchestratorException("Cannot suspend because...")).when(orchestratorMock)
                .suspend("localhost.test.yahoo.com", Arrays.asList("host1.test.yahoo.com", parentHostname));

        // Initially we are denied to suspend because we have to freeze all the node-agents
        assertTrue(verifyWithRetries("suspend/node-admin", false));
        // At this point they should be frozen, but Orchestrator doesn't allow to suspend either the container or the node-admin
        assertTrue(verifyWithRetries("suspend/node-admin", false));

        doNothing().when(orchestratorMock)
                .suspend("localhost.test.yahoo.com", Arrays.asList("host1.test.yahoo.com", parentHostname));

        // Orchestrator successfully suspended everything
        assertTrue(verifyWithRetries("suspend/node-admin", true));

        // Allow stopping services in active nodes
        doNothing().when(dockerOperationsMock)
                .trySuspendNode(eq(new ContainerName("host1")));
        doNothing().when(dockerOperationsMock)
                .stopServicesOnNode(eq(new ContainerName("host1")));

        assertTrue(verifyWithRetries("suspend", false));
        assertTrue(verifyWithRetries("suspend", true));
    }

    private boolean verifyWithRetries(String command, boolean expectedResult) throws IOException, InterruptedException {
        for (int i = 0; i < 10; i++) {
            if (doPutCall(command) == expectedResult) return true;
            Thread.sleep(25);
        }
        return false;
    }


    private String createServiceXml(int port) {
        return "<services version=\"1.0\">\n" +
                "  <jdisc version=\"1.0\" jetty=\"true\">\n" +
                "    <handler id=\"com.yahoo.vespa.hosted.node.admin.restapi.RestApiHandler\" bundle=\"node-admin\">\n" +
                "      <binding>http://*/rest/*</binding>\n" +
                "    </handler>\n" +
                "    <component id=\"metric-receiver\" class=\"com.yahoo.vespa.hosted.node.admin.integrationTests.RunInContainerTest$MetricReceiverWrapperMock\" bundle=\"node-admin\"/>\n" +
                "    <component id=\"node-admin\" class=\"com.yahoo.vespa.hosted.node.admin.integrationTests.RunInContainerTest$NodeAdminProviderWithMocks\" bundle=\"node-admin\"/>\n" +
                "  <http>" +
                "    <server id=\'myServer\' port=\'" + port + "\' />" +
                "  </http>" +
                "  </jdisc>\n" +
                "</services>\n";
    }


    public static class MetricReceiverWrapperMock extends MetricReceiverWrapper {
        public MetricReceiverWrapperMock() {
            super(MetricReceiver.nullImplementation);
        }
    }

    public class NodeAdminProviderWithMocks implements Provider<NodeAdminStateUpdater> {
        private final Duration NODE_AGENT_SCAN_INTERVAL = Duration.ofMillis(100);
        private final Duration NODE_ADMIN_CONVERGE_STATE_INTERVAL = Duration.ofMillis(5);

        private final StorageMaintainer storageMaintainer = mock(StorageMaintainer.class);
        private final AclMaintainer aclMaintainer = mock(AclMaintainer.class);
        private final Environment environment = new Environment.Builder().build();
        private final MetricReceiverWrapper mr = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        private final Function<String, NodeAgent> nodeAgentFactory =
                (hostName) -> new NodeAgentImpl(hostName, nodeRepositoryMock, orchestratorMock, dockerOperationsMock,
                        storageMaintainer, aclMaintainer, environment, Clock.systemUTC(), NODE_AGENT_SCAN_INTERVAL);
        private final NodeAdmin nodeAdmin = new NodeAdminImpl(dockerOperationsMock, nodeAgentFactory, storageMaintainer, aclMaintainer, mr, Clock.systemUTC());
        private final NodeAdminStateUpdaterImpl nodeAdminStateUpdater = new NodeAdminStateUpdaterImpl(nodeRepositoryMock,
                orchestratorMock, storageMaintainer, nodeAdmin, "localhost.test.yahoo.com",
                Clock.systemUTC(), NODE_ADMIN_CONVERGE_STATE_INTERVAL, Optional.of(new ClassLocking()));

        public NodeAdminProviderWithMocks() {
            nodeAdminStateUpdater.start();
        }

        @Override
        public NodeAdminStateUpdaterImpl get() {
            return nodeAdminStateUpdater;
        }

        @Override
        public void deconstruct() {
            nodeAdminStateUpdater.stop();
        }
    }
}
