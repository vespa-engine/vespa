// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import ai.vespa.hosted.client.MockHttpClient;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.ApplicationStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.model.ContentService;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static com.yahoo.vespa.orchestrator.controller.ClusterControllerNodeState.DOWN;
import static com.yahoo.vespa.orchestrator.controller.ClusterControllerNodeState.MAINTENANCE;
import static com.yahoo.vespa.orchestrator.controller.ClusterControllerNodeState.UP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author jonmv
 */
public class ClusterControllerClientImplTest {

    final HostName host = new HostName("node");
    final ManualClock clock = new ManualClock();
    final List<HostName> clusterControllers = List.of(new HostName("host1"), new HostName("host2"), new HostName("host3"));

    MockHttpClient wire;
    RetryingClusterControllerClientFactory factory;
    ClusterControllerClient client;

    @Before
    public void setup() {
        wire = new MockHttpClient();
        factory = new RetryingClusterControllerClientFactory(wire);
        client = factory.createClient(clusterControllers, "cc");
    }

    @After
    public void teardown() {
        factory.deconstruct();
    }

    @Test
    public void verifySetNodeState() {
        OrchestratorContext context = OrchestratorContext.createContextForSingleAppOp(clock);
        wire.expect((url, body) -> {
                        assertEquals("http://host1:19050/cluster/v2/cc/storage/2?timeout=9.6",
                                     url.asURI().toString());
                        assertEquals("{\"state\":{\"user\":{\"reason\":\"Orchestrator\",\"state\":\"down\"}},\"condition\":\"SAFE\"}",
                                     body);
                        return "{ \"wasModified\": true }";
                    },
                    200);
        client.setNodeState(context, host, 2, DOWN, ContentService.STORAGE_NODE, false);

        clock.advance(Duration.ofSeconds(9));
        wire.expect((url, body) -> {
                        assertEquals("http://host1:19050/cluster/v2/cc/storage/1?timeout=0.6",
                                     url.asURI().toString());
                        assertEquals("{\"state\":{\"user\":{\"reason\":\"Orchestrator\",\"state\":\"down\"}},\"condition\":\"SAFE\"}",
                                     body);
                        return "{ \"wasModified\": false, \"reason\": \"because\" }";
                    },
                    200);
        assertEquals("Changing the state of node would violate controller-set-node-state: Failed to set state to DOWN in cluster controller: because",
                     assertThrows(HostStateChangeDeniedException.class,
                                  () -> client.setNodeState(context, host, 1, DOWN, ContentService.STORAGE_NODE, false))
                             .getMessage());
    }

    @Test
    public void verifyProbeNodeState() {
        wire.expect((url, body) -> {
                        assertEquals("http://host1:19050/cluster/v2/cc/storage/2?timeout=59.6",
                                     url.asURI().toString());
                        assertEquals("{\"state\":{\"user\":{\"reason\":\"Orchestrator\",\"state\":\"maintenance\"}},\"condition\":\"SAFE\",\"probe\":true}",
                                     body);
                        return "{ \"wasModified\": false, \"reason\": \"no reason\" }";
                    },
                    200);
        assertFalse(client.trySetNodeState(OrchestratorContext.createContextForBatchProbe(clock), host, 2, MAINTENANCE, ContentService.STORAGE_NODE, false));
    }

    @Test
    public void verifySetApplicationState() {
        wire.expect((url, body) -> {
                        assertEquals("http://host1:19050/cluster/v2/cc?timeout=299.6",
                                     url.asURI().toString());
                        assertEquals("{\"state\":{\"user\":{\"reason\":\"Orchestrator\",\"state\":\"up\"}},\"condition\":\"FORCE\"}",
                                     body);
                        return "{ \"message\": \":<\" }";
                    },
                    500);
        assertEquals("Failed to set application app cluster name cc to cluster state UP due to: " +
                     "got status code 500 for POST http://host1:19050/cluster/v2/cc?timeout=299.6: :<",
                     assertThrows(ApplicationStateChangeDeniedException.class,
                                  () -> client.setApplicationState(OrchestratorContext.createContextForAdminOp(clock),
                                                                   new ApplicationInstanceId("app"),
                                                                   UP))
                             .getMessage());
    }

    @Test
    public void verifyRetriesUntilTimeout() {
        OrchestratorContext context = OrchestratorContext.createContextForSingleAppOp(clock);
        // IOException
        wire.expect(request -> {
            clock.advance(Duration.ofSeconds(6));
            throw new IOException("Oh no!");
        });
        // Redirect
        wire.expect((url, body) -> {
            assertEquals("http://host2:19050/cluster/v2/cc/storage/2?timeout=3.6",
                         url.asURI().toString());
            clock.advance(Duration.ofSeconds(4));
            return "";
        }, 302);
        // Timeout
        assertEquals("Changing the state of node would violate deadline: Timeout while waiting for setNodeState(2, UP) " +
                     "against [host1, host2, host3]: Timed out after PT10S",
                     assertThrows(HostStateChangeDeniedException.class,
                                  () -> client.setNodeState(context, host, 2, UP, ContentService.STORAGE_NODE, false))
                             .getMessage());
    }

    @Test
    public void testRetriesUntilExhaustion() {
        OrchestratorContext context = OrchestratorContext.createContextForSingleAppOp(clock);
        for (int i = 0; i < clusterControllers.size(); i++) {
            int j = i + 1;
            wire.expect((url, body) -> {
                            assertEquals("http://host" + j + ":19050/cluster/v2/cc/storage/2?timeout=9.6",
                                         url.asURI().toString());
                            return "";
                        },
                        503);
        }
        // All retries exhausted
        assertEquals("Changing the state of node would violate controller-set-node-state: Failed setting node 2 in cluster cc to state UP: " +
                     "got status code 503 for POST http://host1:19050/cluster/v2/cc/storage/2?timeout=9.6",
                     assertThrows(HostStateChangeDeniedException.class,
                                  () -> client.setNodeState(context, host, 2, UP, ContentService.STORAGE_NODE, false))
                             .getMessage());
    }

}
