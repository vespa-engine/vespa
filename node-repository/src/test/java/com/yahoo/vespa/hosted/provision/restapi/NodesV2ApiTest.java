// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.Secret;
import ai.vespa.secret.model.SecretVersionId;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.security.KeyFormat;
import com.yahoo.security.KeyUtils;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.maintenance.OsUpgradeActivator;
import com.yahoo.vespa.hosted.provision.maintenance.TestMetric;
import com.yahoo.vespa.hosted.provision.testutils.MockNodeRepository;
import com.yahoo.vespa.hosted.provision.testutils.OrchestratorMock;
import com.yahoo.vespa.hosted.provision.testutils.SecretStoreMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.XECPublicKey;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;

/**
 * Test of the REST APIs provided by the node repository.
 * 
 * Note: This class is referenced from our operations documentation and must not be renamed/moved without updating that.
 * 
 * @author bratseth
 */
public class NodesV2ApiTest {

    private RestApiTester tester;

    @Before
    public void createTester() {
        tester = new RestApiTester(SystemName.main, CloudAccount.from("111222333444"));
    }

    @After
    public void closeTester() {
        tester.close();
    }

    /** This test gives examples of the node requests that can be made to nodes/v2 */
    @Test
    public void test_requests() throws Exception {
        // GET
        assertFile(new Request("http://localhost:8080/nodes/v2/"), "root.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/state/"), "states.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/state/?recursive=true"), "states-recursive.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/state/active?recursive=true"), "active-nodes.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/"), "nodes.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true"), "nodes-recursive.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true&includeDeprovisioned=true"), "nodes-recursive-include-deprovisioned.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?enclave=true"), "enclave-nodes.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true&enclave=true"), "enclave-nodes-recursive.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host2.yahoo.com"), "node2.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/stats"), "stats.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/maintenance/"), "maintenance.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/wireguard/"), "wireguard.json");

        // GET with filters
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true&hostname=host6.yahoo.com%20host2.yahoo.com"), "application2-nodes.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true&clusterType=content"), "content-nodes.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true&clusterId=id2"), "application2-nodes.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true&application=tenant2.application2.instance2"), "application2-nodes.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true&parentHost=dockerhost1.yahoo.com"), "child-nodes.json");

        // POST restart command
        assertRestart(1, new Request("http://localhost:8080/nodes/v2/command/restart?hostname=host2.yahoo.com",
                         new byte[0], Request.Method.POST));
        assertRestart(2, new Request("http://localhost:8080/nodes/v2/command/restart?application=tenant2.application2.instance2",
                         new byte[0], Request.Method.POST));
        assertRestart(15, new Request("http://localhost:8080/nodes/v2/command/restart",
                         new byte[0], Request.Method.POST));
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host2.yahoo.com"),
                                     "\"restartGeneration\":3");

        // POST reboot command
        assertReboot(16, new Request("http://localhost:8080/nodes/v2/command/reboot?state=failed%20active",
                        new byte[0], Request.Method.POST));
        assertReboot(2, new Request("http://localhost:8080/nodes/v2/command/reboot?application=tenant2.application2.instance2",
                        new byte[0], Request.Method.POST));
        assertReboot(19, new Request("http://localhost:8080/nodes/v2/command/reboot",
                        new byte[0], Request.Method.POST));
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host2.yahoo.com"),
                                     "\"rebootGeneration\":3");

        // POST new nodes
        assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                                   ("[" + asHostJson("host8.yahoo.com", "default", List.of("127.0.8.2"), List.of(), "127.0.8.1") + "," + // test with only 1 ip address
                                   asHostJson("host9.yahoo.com", "large-variant", List.of(), List.of("node9-1.yahoo.com"), "127.0.9.1", "::9:1") + "," +
                                   asNodeJson("parent2.yahoo.com", NodeType.host, "large-variant", Optional.of(TenantName.from("myTenant")),
                                           Optional.of(ApplicationId.from("tenant1", "app1", "instance1")), Optional.empty(), List.of(), List.of(), "127.0.127.1", "::127:1") + "," +
                                   asDockerNodeJson("host11.yahoo.com", "parent.host.yahoo.com", "::11") + "]").
                                   getBytes(StandardCharsets.UTF_8),
                                   Request.Method.POST),
                       "{\"message\":\"Added 4 nodes to the provisioned state\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host8.yahoo.com"), "node8.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host9.yahoo.com"), "node9.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host11.yahoo.com"), "node11.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/parent2.yahoo.com"), "parent2.json");

        // POST duplicate node
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                                         ("[" + asNodeJson("host8.yahoo.com", "default", "127.0.254.8") + "]").getBytes(StandardCharsets.UTF_8),
                                         Request.Method.POST), 500,
                             "{\"error-code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"Cannot add provisioned host host8.yahoo.com: A node with this name already exists\"}");

        // DELETE a provisioned node
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host9.yahoo.com",
                                   new byte[0], Request.Method.DELETE),
                       "{\"message\":\"Removed host9.yahoo.com\"}");

        // PUT nodes ready
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/dirty/host8.yahoo.com",
                       new byte[0], Request.Method.PUT),
                "{\"message\":\"Moved host8.yahoo.com to dirty\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/host8.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host8.yahoo.com to ready\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host8.yahoo.com"),
                                                 "\"state\":\"ready\"");
        // calling ready again is a noop:
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/host8.yahoo.com",
                                  new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host8.yahoo.com to ready\"}");

        // PUT a node in failed ...
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed/host2.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host2.yahoo.com to failed and marked none as wantToFail\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host2.yahoo.com"),
                                     "\"state\":\"failed\"");
        // ... and put it back in active (after fixing). This is useful to restore data when multiple nodes fail.
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/active/host2.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host2.yahoo.com to active\"}");

        // or, PUT a node in failed ...
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed/test-node-pool-102-2",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved test-node-pool-102-2 to failed and marked none as wantToFail\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/test-node-pool-102-2"),
                                                 "\"state\":\"failed\"");
        // ... and deallocate it such that it moves to dirty and is recycled
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/dirty/test-node-pool-102-2",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved test-node-pool-102-2 to dirty\"}");

        // ... and set it back to ready as if this was from the node-admin with the temporary state rest api
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/test-node-pool-102-2",
                        new byte[0], Request.Method.PUT),
                "{\"message\":\"Moved test-node-pool-102-2 to ready\"}");

        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/test-node-pool-102-2",  new byte[0], Request.Method.GET),
                             404, "{\"error-code\":\"NOT_FOUND\",\"message\":\"No node with hostname 'test-node-pool-102-2'\"}");

        // Mark a node and its children as want to fail
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed/dockerhost1.yahoo.com", new byte[0], Request.Method.PUT),
                "{\"message\":\"Moved none to failed and marked dockerhost1.yahoo.com, host4.yahoo.com as wantToFail\"}");
        // Nodes are not failed yet
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed"), "{\"nodes\":[" +
                       "{\"url\":\"http://localhost:8080/nodes/v2/node/host5.yahoo.com\"}]}");

        // Update (PATCH) a node (multiple fields can also be sent in one request body)
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"currentRestartGeneration\": 1}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"currentRebootGeneration\": 1}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
        // Patching currentRebootGeneration twice adds another rebooted event
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"currentRebootGeneration\": 1}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"flavor\": \"d-2-8-100\"}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"currentVespaVersion\": \"5.104.142\"}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"parentHostname\": \"parent.yahoo.com\"}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                        Utf8.toBytes("{\"ipAddresses\": [\"127.0.0.1\",\"::1\"]}"), Request.Method.PATCH),
                "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"wantToRetire\": true, \"wantToUpgradeFlavor\": true}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                        Utf8.toBytes("{\"currentVespaVersion\": \"6.43.0\",\"currentDockerImage\": \"docker-registry.domain.tld:8080/dist/vespa:6.45.0\"}"), Request.Method.PATCH),
                        "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                        Utf8.toBytes("{\"id\": \"patched-id\"}"), Request.Method.PATCH),
                "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                   Utf8.toBytes("{\"modelName\": \"foo\"}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                        Utf8.toBytes("{\"wantToRetire\": true}"), Request.Method.PATCH),
                "{\"message\":\"Updated dockerhost1.yahoo.com\"}");

        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                   Utf8.toBytes("{\"wantToDeprovision\": true}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                   Utf8.toBytes("{\"wantToDeprovision\": false, \"wantToRetire\": false}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost2.yahoo.com",
                                   Utf8.toBytes("{\"wantToDeprovision\": true, \"wantToRetire\": true}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost2.yahoo.com\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host5.yahoo.com"),
                "\"wantToRetire\":true,\"preferToRetire\":false,\"wantToDeprovision\":true,");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                   Utf8.toBytes("{\"wantToRebuild\": true, \"wantToRetire\": true}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");

        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com"), "\"modelName\":\"foo\"");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                   Utf8.toBytes("{\"modelName\": null}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        tester.assertPartialResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com"), "modelName", false);

        ((OrchestratorMock) tester.container().components().getComponent(OrchestratorMock.class.getName()))
                .suspend(new HostName("host4.yahoo.com"));

        assertFile(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"), "node4-after-changes.json");

        // Park and remove host
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/parked/dockerhost1.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved dockerhost1.yahoo.com to parked\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                   new byte[0], Request.Method.DELETE),
                       "{\"message\":\"Removed dockerhost1.yahoo.com\"}");

        // Host marked for rebuild cannot be forgotten
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                   Utf8.toBytes("{\"wantToRebuild\": true, \"wantToRetire\": true}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                          new byte[0], Request.Method.DELETE),
                              400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"deprovisioned host dockerhost1.yahoo.com is rebuilding and cannot be forgotten\"}");

        // Forget host completely
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                   Utf8.toBytes("{\"wantToRebuild\": false}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                   new byte[0], Request.Method.DELETE),
                       "{\"message\":\"Permanently removed dockerhost1.yahoo.com\"}");
    }

    @Test
    public void test_application_requests() throws Exception {
        assertFile(new Request("http://localhost:8080/nodes/v2/application/"), "applications.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/application/tenant1.application1.instance1"),
                   "application1.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/application/tenant2.application2.instance2"),
                   "application2.json");

        // Update (PATCH) an application
        assertResponse(new Request("http://localhost:8080/nodes/v2/application/tenant1.application1.instance1",
                                   Utf8.toBytes("{\"currentReadShare\": 0.3, " +
                                                "\"maxReadShare\": 0.5 }"), Request.Method.PATCH),
                       "{\"message\":\"Updated application 'tenant1.application1.instance1'\"}");
    }

    @Test
    public void post_with_patch_method_override_in_header_is_handled_as_patch() throws Exception  {
        Request req = new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                Utf8.toBytes("{\"currentRestartGeneration\": 1}"), Request.Method.POST);
        req.getHeaders().add("X-HTTP-Method-Override", "PATCH");
        assertResponse(req, "{\"message\":\"Updated host4.yahoo.com\"}");
    }

    @Test
    public void post_with_invalid_method_override_in_header_gives_sane_error_message() throws Exception  {
        Request req = new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                Utf8.toBytes("{\"currentRestartGeneration\": 1}"), Request.Method.POST);
        req.getHeaders().add("X-HTTP-Method-Override", "GET");
        tester.assertResponse(req, 400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Illegal X-HTTP-Method-Override header for POST request. Accepts 'PATCH' but got 'GET'\"}");
    }

    @Test
    public void post_node_with_ip_address() throws Exception {
        assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                        ("[" + asNodeJson("ipv4-host.yahoo.com", "default","127.0.0.1") + "]").
                                getBytes(StandardCharsets.UTF_8),
                        Request.Method.POST),
                "{\"message\":\"Added 1 nodes to the provisioned state\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                        ("[" + asNodeJson("ipv6-host.yahoo.com", "default", "::1") + "]").
                                getBytes(StandardCharsets.UTF_8),
                        Request.Method.POST),
                "{\"message\":\"Added 1 nodes to the provisioned state\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                        ("[" + asNodeJson("dual-stack-host.yahoo.com", "default", "127.0.254.254", "::254:254") + "]").
                                getBytes(StandardCharsets.UTF_8),
                        Request.Method.POST),
                "{\"message\":\"Added 1 nodes to the provisioned state\"}");
    }

    @Test
    public void post_node_with_duplicate_ip_address() throws Exception {
        Request req = new Request("http://localhost:8080/nodes/v2/node",
                ("[" + asNodeJson("host-with-ip.yahoo.com", "default", "foo") + "]").
                        getBytes(StandardCharsets.UTF_8),
                Request.Method.POST);
        tester.assertResponse(req, 400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Invalid IP address 'foo': 'foo' is not an IP string literal.\"}");

        // Attempt to POST tenant node with already assigned IP
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                                         "[" + asNodeJson("tenant-node-foo.yahoo.com", "default", "127.0.1.1") + "]",
                                          Request.Method.POST), 400,
                             "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot assign [127.0.1.1] to tenant-node-foo.yahoo.com: [127.0.1.1] already assigned to host1.yahoo.com\"}");

        // Attempt to PATCH existing tenant node with already assigned IP
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/test-node-pool-102-2",
                                         "{\"ipAddresses\": [\"127.0.2.1\"]}",
                                          Request.Method.PATCH), 400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'ipAddresses': Cannot assign [127.0.2.1] to test-node-pool-102-2: [127.0.2.1] already assigned to host2.yahoo.com\"}");

        // Attempt to POST host node with already assigned IP
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                                         "[" + asHostJson("host200.yahoo.com", "default", List.of(), List.of(), "127.0.2.1") + "]",
                                          Request.Method.POST), 400,
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot assign [127.0.2.1] to host200.yahoo.com: [127.0.2.1] already assigned to host2.yahoo.com\"}");

        // Attempt to PATCH host node with IP in the pool of another node
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                         "{\"ipAddresses\": [\"::104:3\"]}",
                                         Request.Method.PATCH), 400,
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'ipAddresses': Cannot assign [::100:4, ::100:3, ::100:2, ::104:3] to dockerhost1.yahoo.com: [::104:3] already assigned to dockerhost5.yahoo.com\"}");

        // Node types running a single container can share their IP address with child node
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                                         "[" + asNodeJson("cfghost42.yahoo.com", NodeType.confighost, "default", Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), "127.0.42.1") + "]",
                                         Request.Method.POST), 200,
                       "{\"message\":\"Added 1 nodes to the provisioned state\"}");
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                                         "[" + asDockerNodeJson("cfg42.yahoo.com", NodeType.config, "cfghost42.yahoo.com", "127.0.42.1") + "]",
                                         Request.Method.POST), 200,
                       "{\"message\":\"Added 1 nodes to the provisioned state\"}");

        // ... but cannot share with child node of wrong type
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                                         "[" + asDockerNodeJson("proxy42.yahoo.com", NodeType.proxy, "cfghost42.yahoo.com", "127.0.42.1") + "]",
                                         Request.Method.POST), 400,
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot assign [127.0.42.1] to proxy42.yahoo.com: [127.0.42.1] already assigned to cfg42.yahoo.com\"}");

        // ... nor with child node on different host
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                                         "[" + asNodeJson("cfghost43.yahoo.com", NodeType.confighost, "default", Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), "127.0.43.1") + "]",
                                          Request.Method.POST), 200,
                       "{\"message\":\"Added 1 nodes to the provisioned state\"}");
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/cfg42.yahoo.com",
                                         "{\"ipAddresses\": [\"127.0.43.1\"]}",
                                          Request.Method.PATCH), 400,
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'ipAddresses': Cannot assign [127.0.43.1] to cfg42.yahoo.com: [127.0.43.1] already assigned to cfghost43.yahoo.com\"}");
    }

    @Test
    public void patch_hostnames() throws IOException {
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"), "node4.json");

        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                        Utf8.toBytes("{\"additionalHostnames\": [\"a\",\"b\"]}"), Request.Method.PATCH),
                "{\"message\":\"Updated host4.yahoo.com\"}");

        assertFile(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"), "node4-with-hostnames.json");

        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                        Utf8.toBytes("{\"additionalHostnames\": []}"), Request.Method.PATCH),
                "{\"message\":\"Updated host4.yahoo.com\"}");

        assertFile(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"), "node4.json");
    }

    @Test
    public void patch_wireguard_pubkey() throws IOException {
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"), "node4.json");

        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                        Utf8.toBytes("{\"wireguard\":{\"key\": \"not a wg key\"}}"), Request.Method.PATCH), 400,
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'wireguard': Wireguard key must match '^[A-Za-z0-9+/]{42}[AEIMQUYcgkosw480]=$', but got: 'not a wg key'\"}");

        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                        Utf8.toBytes("{\"wireguard\":{\"key\": \"lololololololololololololololololololololoo=\"}}"), Request.Method.PATCH),
                "{\"message\":\"Updated host4.yahoo.com\"}");

        assertFile(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"), "node4-wg.json");
    }

    @Test
    public void post_controller_node() throws Exception {
        String data = "[{\"hostname\":\"controller1.yahoo.com\", \"id\":\"fake-controller1.yahoo.com\"," +
                      createIpAddresses("127.0.0.1") +
                      "\"flavor\":\"default\"" +
                      ", \"type\":\"controller\"}]";
        assertResponse(new Request("http://localhost:8080/nodes/v2/node", data.getBytes(StandardCharsets.UTF_8),
                                   Request.Method.POST),
                       "{\"message\":\"Added 1 nodes to the provisioned state\"}");

        assertFile(new Request("http://localhost:8080/nodes/v2/node/controller1.yahoo.com"), "controller1.json");
    }

    @Test
    public void fails_to_ready_node_with_hard_fail() throws Exception {
        assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                        ("[" + asHostJson("host12.yahoo.com", "default", List.of(), List.of()) + "]").
                                getBytes(StandardCharsets.UTF_8),
                        Request.Method.POST),
                "{\"message\":\"Added 1 nodes to the provisioned state\"}");
        String msg = "Actual disk space (2TB) differs from spec (3TB)";
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host12.yahoo.com",
                        Utf8.toBytes("{\"reports\":{\"diskSpace\":{\"createdMillis\":2,\"description\":\"" + msg + "\",\"type\": \"HARD_FAIL\"}}}"),
                        Request.Method.PATCH),
                "{\"message\":\"Updated host12.yahoo.com\"}");
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/host12.yahoo.com", new byte[0], Request.Method.PUT),
                                           400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"provisioned host host12.yahoo.com cannot be readied because it has " +
                              "hard failures: [diskSpace reported 1970-01-01T00:00:00.002Z: " + msg + "]\"}");
    }

    @Test
    public void patching_dirty_node_does_not_increase_reboot_generation() throws Exception {
        assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                        ("[" + asNodeJson("foo.yahoo.com", "default") + "]").
                                getBytes(StandardCharsets.UTF_8),
                        Request.Method.POST),
                "{\"message\":\"Added 1 nodes to the provisioned state\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed/foo.yahoo.com",
                        new byte[0], Request.Method.PUT),
                "{\"message\":\"Moved foo.yahoo.com to failed and marked none as wantToFail\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/dirty/foo.yahoo.com",
                        new byte[0], Request.Method.PUT),
                "{\"message\":\"Moved foo.yahoo.com to dirty\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/foo.yahoo.com"),
                                      "\"rebootGeneration\":0");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/foo.yahoo.com",
                        Utf8.toBytes("{\"currentRebootGeneration\": 42}"), Request.Method.PATCH),
                "{\"message\":\"Updated foo.yahoo.com\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/foo.yahoo.com"),
                                      "\"rebootGeneration\":0");
    }

    @Test
    public void acls_for_inclave_tenant_host() throws Exception {
        assertFile(new Request("http://localhost:8080/nodes/v2/acl/host5.yahoo.com"), "acl-tenant-node.json");
    }

    @Test
    public void acl_request_by_config_server() throws Exception {
        assertFile(new Request("http://localhost:8080/nodes/v2/acl/cfg1.yahoo.com"), "acl-config-server.json");
    }

    @Test
    public void test_invalid_requests() throws Exception {
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/node-does-not-exist",
                                          new byte[0], Request.Method.GET),
                       404, "{\"error-code\":\"NOT_FOUND\",\"message\":\"No node with hostname 'node-does-not-exist'\"}");

        // Attempt to fail and ready an allocated node without going through dirty
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed/node-does-not-exist",
                                          new byte[0], Request.Method.PUT),
                       404, "{\"error-code\":\"NOT_FOUND\",\"message\":\"No node with hostname 'node-does-not-exist'\"}");

        // Attempt to fail and ready an allocated node without going through dirty
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed/host1.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host1.yahoo.com to failed and marked none as wantToFail\"}");
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/host1.yahoo.com",
                                          new byte[0], Request.Method.PUT),
                       400,
                        "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot make failed host host1.yahoo.com allocated to tenant1.application1.instance1 as 'container/id1/0/0' available for new allocation as it is not in state [dirty]\"}");

        // (... while dirty then ready works (the ready move will be initiated by node maintenance))
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/dirty/host1.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host1.yahoo.com to dirty\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/host1.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host1.yahoo.com to ready\"}");

        // Attempt to park and ready an allocated node without going through dirty
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/parked/host2.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host2.yahoo.com to parked\"}");
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/host2.yahoo.com",
                                          new byte[0], Request.Method.PUT),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot make parked host host2.yahoo.com allocated to tenant2.application2.instance2 as 'content/id2/0/0/stateful' available for new allocation as it is not in state [dirty]\"}");
        // (... while dirty then ready works (the ready move will be initiated by node maintenance))
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/dirty/host2.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host2.yahoo.com to dirty\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/host2.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host2.yahoo.com to ready\"}");

        // Attempt to DELETE a node which has been removed
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/host2.yahoo.com",
                                          new byte[0], Request.Method.DELETE),
                       404, "{\"error-code\":\"NOT_FOUND\",\"message\":\"No node with hostname 'host2.yahoo.com'\"}");

        // Attempt to DELETE allocated node
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                          new byte[0], Request.Method.DELETE),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"active child node host4.yahoo.com allocated to tenant3.application3.instance3 as 'content/id3/0/0/stateful' is currently allocated and cannot be removed while in active\"}");

        // PUT current restart generation with string instead of long
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                          Utf8.toBytes("{\"currentRestartGeneration\": \"1\"}"), Request.Method.PATCH),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'currentRestartGeneration': Expected a LONG value, got a STRING\"}");

        // PUT flavor with long instead of string
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                          Utf8.toBytes("{\"flavor\": 1}"), Request.Method.PATCH),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'flavor': Expected a STRING value, got a LONG\"}");

        // Attempt to set nonexisting node to active
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/state/active/host2.yahoo.com",
                                          new byte[0], Request.Method.PUT), 404,
                              "{\"error-code\":\"NOT_FOUND\",\"message\":\"No node with hostname 'host2.yahoo.com'\"}");

        // Attempt to POST duplicate nodes
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                                          ("[" + asNodeJson("host8.yahoo.com", "default", "127.0.254.1", "::254:1") + "," +
                                           asNodeJson("host8.yahoo.com", "large-variant", "127.0.253.1", "::253:1") + "]").getBytes(StandardCharsets.UTF_8),
                                          Request.Method.POST), 400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot add nodes: provisioned host host8.yahoo.com is duplicated in the argument list\"}");

        // Attempt to PATCH field not relevant for child node
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/test-node-pool-102-2",
                                          Utf8.toBytes("{\"modelName\": \"foo\"}"), Request.Method.PATCH),
                              400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'modelName': A child node cannot have model name set\"}");
    }

    @Test
    public void test_node_patching() throws Exception {
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{" +
                                           "\"currentRestartGeneration\": 1," +
                                           "\"currentRebootGeneration\": 3," +
                                           "\"flavor\": \"medium-disk\"," +
                                           "\"currentVespaVersion\": \"5.104.142\"," +
                                           "\"failCount\": 0," +
                                           "\"parentHostname\": \"parent.yahoo.com\"" +
                                       "}"
                                   ),
                                   Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");

        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/doesnotexist.yahoo.com",
                                          Utf8.toBytes("{\"currentRestartGeneration\": 1}"),
                                          Request.Method.PATCH),
                              404, "{\"error-code\":\"NOT_FOUND\",\"message\":\"No node with hostname 'doesnotexist.yahoo.com'\"}");

        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/host5.yahoo.com",
                                          Utf8.toBytes("{\"currentRestartGeneration\": 1}"),
                                          Request.Method.PATCH),
                              400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'currentRestartGeneration': Node is not allocated\"}");
    }

    @Test
    public void test_node_patch_to_remove_docker_ready_fields() throws Exception {
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host5.yahoo.com",
                        Utf8.toBytes("{" +
                                "\"currentVespaVersion\": \"\"," +
                                "\"currentDockerImage\": \"\"" +
                                "}"
                        ),
                        Request.Method.PATCH),
                "{\"message\":\"Updated host5.yahoo.com\"}");

        assertFile(new Request("http://localhost:8080/nodes/v2/node/host5.yahoo.com"), "node5-after-changes.json");
    }

    @Test
    public void test_reports_patching() throws IOException {
        // Add report
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                        Utf8.toBytes("{" +
                                "  \"reports\": {" +
                                "    \"actualCpuCores\": {" +
                                "      \"createdMillis\": 1, " +
                                "      \"description\": \"Actual number of CPU cores (2) differs from spec (4)\"," +
                                "      \"type\": \"HARD_FAIL\"," +
                                "      \"value\":2" +
                                "    }," +
                                "    \"diskSpace\": {" +
                                "      \"createdMillis\": 2, " +
                                "      \"description\": \"Actual disk space (2TB) differs from spec (3TB)\"," +
                                "      \"type\": \"HARD_FAIL\"," +
                                "      \"details\": {" +
                                "        \"inGib\": 3," +
                                "        \"disks\": [\"/dev/sda1\", \"/dev/sdb3\"]" +
                                "      }" +
                                "    }" +
                                "  }" +
                                "}"),
                        Request.Method.PATCH),
                "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com"), "docker-node1-reports.json");

        // Patching with an empty reports is no-op
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                          Utf8.toBytes("{\"reports\": {}}"),
                                          Request.Method.PATCH),
                              200,
                              "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com"), "docker-node1-reports.json");

        // Patching existing report overwrites
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                          Utf8.toBytes("{" +
                                                       "  \"reports\": {" +
                                                       "    \"actualCpuCores\": {" +
                                                       "      \"createdMillis\": 3 " +
                                                       "    }" +
                                                       "  }" +
                                                       "}"),
                                          Request.Method.PATCH),
                              200,
                              "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com"), "docker-node1-reports-2.json");

        // Clearing one report
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                        Utf8.toBytes("{\"reports\": { \"diskSpace\": null } }"),
                        Request.Method.PATCH),
                "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com"), "docker-node1-reports-3.json");

        // Clearing all reports
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                        Utf8.toBytes("{\"reports\": null }"),
                        Request.Method.PATCH),
                "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com"), "docker-node1-reports-4.json");
    }

    @Test
    public void drop_documents() throws IOException {
        // Initially no reports
        tester.assertPartialResponse(new Request("http://localhost:8080/nodes/v2/node/test-node-pool-102-2"), "reports", false);
        tester.assertPartialResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"), "reports", false);

        // Initiating drop documents will set the report on all nodes
        assertResponse(new Request("http://localhost:8080/nodes/v2/application/tenant3.application3.instance3/drop-documents?clusterId=id3", new byte[0], Request.Method.POST),
                "{\"message\":\"Triggered dropping of documents on 2 nodes\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/test-node-pool-102-2"),
                "{\"dropDocuments\":{\"createdMillis\":123}}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"),
                "{\"dropDocuments\":{\"createdMillis\":123}}");

        // Host admin of the first node finishes dropping
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                        Utf8.toBytes("{\"reports\": {\"dropDocuments\":{\"createdMillis\":25,\"droppedAt\":36}}}"),
                        Request.Method.PATCH),
                "{\"message\":\"Updated host4.yahoo.com\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"),
                "{\"dropDocuments\":{\"createdMillis\":25,\"droppedAt\":36}}");

        // Host admin of the second node finishes dropping, node-repo will update report on both nodes to start phase 2
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/test-node-pool-102-2",
                        Utf8.toBytes("{\"reports\": {\"dropDocuments\":{\"createdMillis\":49,\"droppedAt\":456}}}"),
                        Request.Method.PATCH),
                "{\"message\":\"Updated test-node-pool-102-2\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/test-node-pool-102-2"),
                "{\"dropDocuments\":{\"createdMillis\":49,\"droppedAt\":456,\"readiedAt\":123}}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"),
                "{\"dropDocuments\":{\"createdMillis\":25,\"droppedAt\":36,\"readiedAt\":123}}");

        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/application/does.not.exist/drop-documents", new byte[0], Request.Method.POST),
                404,
                "{\"error-code\":\"NOT_FOUND\",\"message\":\"No content nodes found for does.not.exist\"}");
    }

    @Test
    public void test_upgrade() throws IOException {
        // Initially, no versions are set
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/"), "{\"versions\":{},\"osVersions\":{},\"dockerImages\":{}}");

        // Set version for config, confighost and controller
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/config",
                        Utf8.toBytes("{\"version\": \"6.123.456\"}"),
                        Request.Method.PATCH),
                "{\"message\":\"Set version to 6.123.456 for nodes of type config\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                        Utf8.toBytes("{\"version\": \"6.123.456\"}"),
                        Request.Method.PATCH),
                "{\"message\":\"Set version to 6.123.456 for nodes of type confighost\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/controller",
                                   Utf8.toBytes("{\"version\": \"6.123.456\"}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Set version to 6.123.456 for nodes of type controller\"}");


        // Verify versions are set
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/"),
                "{\"versions\":{\"config\":\"6.123.456\",\"confighost\":\"6.123.456\",\"controller\":\"6.123.456\"},\"osVersions\":{},\"dockerImages\":{}}");

        // Setting version for unsupported node type fails
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/tenant",
                                          Utf8.toBytes("{\"version\": \"6.123.456\"}"),
                                          Request.Method.PATCH),
                              400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Target version for type tenant is not allowed\"}");

        // Omitting version field fails
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                                          Utf8.toBytes("{}"),
                                          Request.Method.PATCH),
                              400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"At least one of 'version' or 'osVersion' must be set\"}");

        // Downgrade without force fails
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                                          Utf8.toBytes("{\"version\": \"6.123.1\"}"),
                                          Request.Method.PATCH),
                              400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot downgrade version without setting 'force'. " +
                              "Current target version: 6.123.456, attempted to set target version: 6.123.1\"}");

        // Downgrade with force is OK
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                        Utf8.toBytes("{\"version\": \"6.123.1\",\"force\":true}"),
                        Request.Method.PATCH),
                "{\"message\":\"Set version to 6.123.1 for nodes of type confighost\"}");

        // Verify version has been updated
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/"),
                "{\"versions\":{\"config\":\"6.123.456\",\"confighost\":\"6.123.1\",\"controller\":\"6.123.456\"},\"osVersions\":{},\"dockerImages\":{}}");

        // Setting empty version without force fails
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                                          Utf8.toBytes("{\"version\": null}"),
                                          Request.Method.PATCH),
                              400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot downgrade version without setting 'force'. Current target version: 6.123.1, attempted to set target version: 0.0.0\"}");

        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                        Utf8.toBytes("{\"version\": null, \"force\": true}"),
                        Request.Method.PATCH),
                "{\"message\":\"Set version to 0.0.0 for nodes of type confighost\"}");

        // Verify version has been removed
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/"),
                "{\"versions\":{\"config\":\"6.123.456\",\"controller\":\"6.123.456\"},\"osVersions\":{},\"dockerImages\":{}}");

        // Upgrade OS for confighost and host
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                                   Utf8.toBytes("{\"osVersion\": \"7.5.2\"}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Set osVersion to 7.5.2 for nodes of type confighost\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/host",
                                   Utf8.toBytes("{\"osVersion\": \"7.5.2\"}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Set osVersion to 7.5.2 for nodes of type host\"}");

        // OS versions are set
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/"),
                       "{\"versions\":{\"config\":\"6.123.456\",\"controller\":\"6.123.456\"},\"osVersions\":{\"host\":\"7.5.2\",\"confighost\":\"7.5.2\"},\"dockerImages\":{}}");

        // Upgrade OS and Vespa together
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                                   Utf8.toBytes("{\"version\": \"6.124.42\", \"osVersion\": \"7.5.2\"}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Set version to 6.124.42, osVersion to 7.5.2 for nodes of type confighost\"}");

        // Attempt to upgrade unsupported node type
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/config",
                                          Utf8.toBytes("{\"osVersion\": \"7.5.2\"}"),
                                          Request.Method.PATCH),
                              400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Node type 'config' does not support OS upgrades\"}");

        // Attempt to downgrade OS
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                                          Utf8.toBytes("{\"osVersion\": \"7.4.2\"}"),
                                          Request.Method.PATCH),
                              400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot set target OS version to 7.4.2 without setting 'force', as it's lower than the current version: 7.5.2\"}");

        // Downgrading OS with force succeeds
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                                   Utf8.toBytes("{\"osVersion\": \"7.4.2\", \"force\": true}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Set osVersion to 7.4.2 for nodes of type confighost\"}");

        // Current target is considered bad, remove it
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                                          Utf8.toBytes("{\"osVersion\": null}"),
                                          Request.Method.PATCH),
                              200,
                              "{\"message\":\"Set osVersion to null for nodes of type confighost\"}");
    }

    @Test
    public void test_os_version() throws Exception {
        // Schedule OS upgrade
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/host",
                                   Utf8.toBytes("{\"osVersion\": \"7.5.2\"}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Set osVersion to 7.5.2 for nodes of type host\"}");

        var nodeRepository = (NodeRepository) tester.container().components().getComponent(MockNodeRepository.class.getName());

        // Activate target
        var osUpgradeActivator = new OsUpgradeActivator(nodeRepository, Duration.ofDays(1), new TestMetric());
        osUpgradeActivator.run();

        // Other node type does not return wanted OS version
        Response r = tester.container().handleRequest(new Request("http://localhost:8080/nodes/v2/node/host1.yahoo.com"));
        assertFalse("Response omits wantedOsVersion field", r.getBodyAsString().contains("wantedOsVersion"));

        // Node updates its node object after upgrading OS
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                   Utf8.toBytes("{\"currentOsVersion\": \"7.5.2\"}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com"), "docker-node1-os-upgrade-complete.json");

        // Override wantedOsVersion
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                   Utf8.toBytes("{\"wantedOsVersion\": \"7.5.3\"}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                   Utf8.toBytes("{\"wantedOsVersion\": \"7.5.2\"}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");

        // Another node upgrades
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost2.yahoo.com",
                                   Utf8.toBytes("{\"currentOsVersion\": \"7.5.2\"}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost2.yahoo.com\"}");

        // Filter nodes by osVersion
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/?osVersion=7.5.2"),
                       "{\"nodes\":[" +
                       "{\"url\":\"http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com\"}," +
                       "{\"url\":\"http://localhost:8080/nodes/v2/node/dockerhost2.yahoo.com\"}" +
                       "]}");
    }

    @Test
    public void test_snapshots() throws IOException {
        SecretStoreMock secretStore = (SecretStoreMock) tester.container().components()
                                                              .getComponent(SecretStoreMock.class.getName());
        KeyPair keyPair = KeyUtils.generateX25519KeyPair();
        secretStore.add(new Secret(Key.fromString("snapshot/sealingPrivateKey"),
                                   KeyUtils.toPem(keyPair.getPrivate(), KeyFormat.PKCS8).getBytes(),
                                   SecretVersionId.of("1")));

        // Trigger creation of snapshots
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com",
                                                  new byte[0], Request.Method.POST),
                                      "{\"message\":\"Triggered a new snapshot of host4.yahoo.com:");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com",
                                                  new byte[0], Request.Method.POST),
                                      "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot trigger new snapshot: Node host4.yahoo.com is busy with snapshot");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/snapshot/host2.yahoo.com",
                                                  new byte[0], Request.Method.POST),
                                      "{\"message\":\"Triggered a new snapshot of host2.yahoo.com:");

        // List snapshots
        String listResponse = tester.container()
                                    .handleRequest(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com"))
                                    .getBodyAsString();
        String id0 = SlimeUtils.entriesStream(SlimeUtils.jsonToSlime(listResponse).get().field("snapshots"))
                               .findFirst().get()
                               .field("id").asString();
        assertFile(new Request("http://localhost:8080/nodes/v2/snapshot"), "snapshot/list.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com"), "snapshot/list-host.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com/" + id0), "snapshot/single.json");

        // Update snapshot state
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com/" + id0,
                                          """
                                                  {"state": "created"}
                                                  """,
                                          Request.Method.PATCH),
                              "{\"message\":\"Updated snapshot '" + id0 + "' for node host4.yahoo.com\"}");

        // List snapshots
        assertFile(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com"), "snapshot/list-host-updated.json");

        // Get node
        tester.assertFile(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"), "snapshot/node4.json");

        // Retrieve encryption key
        String receiverPublicKey = KeyUtils.toBase64EncodedX25519PublicKey((XECPublicKey) KeyUtils.generateX25519KeyPair().getPublic());
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com/" + id0 + "/key",
                                                  "{\"sealingKey\":\"" + receiverPublicKey + "\"}", Request.Method.POST),
                                      "{\"sealedSharedKey\"");

        // Trigger another snapshot
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com",
                                                  new byte[0], Request.Method.POST),
                                      "{\"message\":\"Triggered a new snapshot of host4.yahoo.com:");
        listResponse = tester.container()
                                    .handleRequest(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com"))
                                    .getBodyAsString();
        String id1 = SlimeUtils.entriesStream(SlimeUtils.jsonToSlime(listResponse).get().field("snapshots"))
                               .toList().get(1).field("id").asString();

        // Cannot trigger restore while busy with a different snapshot
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com/" + id0 + "/restore",
                                          new byte[0],
                                          Request.Method.POST),
                              400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot restore snapshot: Node host4.yahoo.com is busy with snapshot " + id1 + " in creating state\"}");

        // Cannot change state of a non-active snapshot
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com/" + id0,
                                          """
                                                  {"state": "restored"}
                                                  """,
                                          Request.Method.PATCH),
                              400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot move snapshot " + id0 + " to restored: Node host4.yahoo.com is not working on this snapshot\"}");

        // Snapshot completes
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com/" + id1,
                                          """
                                                  {"state": "created"}
                                                  """,
                                          Request.Method.PATCH),
                              "{\"message\":\"Updated snapshot '" + id1 + "' for node host4.yahoo.com\"}");

        // Start restore of snapshot
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com/" + id1 + "/restore",
                                          new byte[0],
                                          Request.Method.POST),
                              200,
                              "{\"message\":\"Triggered restore of snapshot '" + id1 + "' to host4.yahoo.com\"}");

        // Forget about snapshot
        assertResponse(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com/" + id0, new byte[0], Request.Method.DELETE),
                       "{\"message\":\"Removed snapshot '" + id0 + "' belonging to host4.yahoo.com\"}");

        // Forgetting about active snapshot fails
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com/" + id1, new byte[0], Request.Method.DELETE),
                              400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot remove snapshot " + id1 + ": Node host4.yahoo.com is working on this snapshot\"}");
        // ..., but succeeds with force
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/snapshot/host4.yahoo.com/" + id1 + "?force=true", new byte[0], Request.Method.DELETE),
                              200,
                              "{\"message\":\"Removed snapshot '" + id1 + "' belonging to host4.yahoo.com\"}");
    }

    @Test
    public void test_firmware_upgrades() throws IOException {
        // dockerhost1 checks firmware at time 100.
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                   Utf8.toBytes("{\"currentFirmwareCheck\":100}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");

        // Schedule a firmware check at time 123 (the mock default).
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/firmware", new byte[0], Request.Method.POST),
                       "{\"message\":\"Will request firmware checks on all hosts.\"}");

        // dockerhost1 displays both values.
        assertFile(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com"),
                   "dockerhost1-with-firmware-data.json");

        // host1 has no wantedFirmwareCheck, as it's not a docker host.
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host1.yahoo.com"),
                   "node1.json");

        // Cancel the firmware check.
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/firmware", new byte[0], Request.Method.DELETE),
                       "{\"message\":\"Cancelled outstanding requests for firmware checks\"}");
    }

    @Test
    public void test_capacity() throws Exception {
        assertFile(new Request("http://localhost:8080/nodes/v2/capacity/?json=true"), "capacity-zone.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/capacity?json=true"), "capacity-zone.json");

        List<String> hostsToRemove = List.of(
                "dockerhost1.yahoo.com",
                "dockerhost2.yahoo.com",
                "dockerhost3.yahoo.com",
                "dockerhost4.yahoo.com"
        );
        String requestUriTemplate = "http://localhost:8080/nodes/v2/capacity/?json=true&hosts=%s";

        assertFile(new Request(String.format(requestUriTemplate,
                                             String.join(",", hostsToRemove.subList(0, 3)))),
                   "capacity-hostremoval-possible.json");
        assertFile(new Request(String.format(requestUriTemplate,
                                             String.join(",", hostsToRemove))),
                   "capacity-hostremoval-impossible.json");
    }


    /** Tests the rendering of each node separately to make it easier to find errors */
    @Test
    public void test_single_node_rendering() throws Exception {
        for (int i = 1; i <= 14; i++) {
            if (i == 8 || i == 9 || i == 11 || i == 12) continue; // these nodes are added later
            assertFile(new Request("http://localhost:8080/nodes/v2/node/host" + i + ".yahoo.com"), "node" + i + ".json");
        }
    }

    @Test
    public void test_flavor_overrides() throws Exception {
        String host = "parent2.yahoo.com";
        // Test adding with overrides
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                                          ("[{\"hostname\":\"" + host + "\"," + createIpAddresses("::1") + "\"id\":\"osid-123\"," +
                                           "\"flavor\":\"large-variant\",\"resources\":{\"diskGb\":1234,\"memoryGb\":4321}}]").getBytes(StandardCharsets.UTF_8),
                                          Request.Method.POST),
                400,
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Can only override disk GB for configured flavor\"}");

        assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                        ("[{\"hostname\":\"" + host + "\"," + createIpAddresses("::1") + "\"id\":\"osid-123\"," +
                                "\"flavor\":\"large-variant\",\"type\":\"host\",\"resources\":{\"diskGb\":1234}}]").
                                getBytes(StandardCharsets.UTF_8),
                        Request.Method.POST),
                "{\"message\":\"Added 1 nodes to the provisioned state\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/" + host),
                                      "\"resources\":{\"vcpu\":64.0,\"memoryGb\":128.0,\"diskGb\":1234.0,\"bandwidthGbps\":15.0,\"diskSpeed\":\"fast\",\"storageType\":\"remote\",\"architecture\":\"x86_64\"}");

        // Test adding tenant node
        String tenant = "node-1-3.yahoo.com";
        String resources = "\"resources\":{\"vcpu\":64.0,\"memoryGb\":128.0,\"diskGb\":1234.0,\"bandwidthGbps\":15.0,\"diskSpeed\":\"slow\",\"storageType\":\"remote\",\"architecture\":\"x86_64\"}";
        assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                        ("[{\"hostname\":\"" + tenant + "\"," + createIpAddresses("::2") + "\"id\":\"osid-124\"," +
                                "\"type\":\"tenant\"," + resources + "}]").
                                getBytes(StandardCharsets.UTF_8),
                        Request.Method.POST),
                "{\"message\":\"Added 1 nodes to the provisioned state\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/" + tenant), resources);

        // Test patching with overrides
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node/" + host,
                                          "{\"diskGb\":5432,\"memoryGb\":2345}".getBytes(StandardCharsets.UTF_8),
                                          Request.Method.PATCH),
                              400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'memoryGb': Can only override disk GB for configured flavor\"}");

        assertResponse(new Request("http://localhost:8080/nodes/v2/node/" + host,
                        "{\"diskGb\":5432}".getBytes(StandardCharsets.UTF_8),
                        Request.Method.PATCH),
                "{\"message\":\"Updated " + host + "\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/" + host),
                                      "\"resources\":{\"vcpu\":64.0,\"memoryGb\":128.0,\"diskGb\":5432.0,\"bandwidthGbps\":15.0,\"diskSpeed\":\"fast\",\"storageType\":\"remote\",\"architecture\":\"x86_64\"}");
    }

    @Test
    public void test_node_resources() throws Exception {
        String hostname = "node123.yahoo.com";
        String resources = "\"resources\":{\"vcpu\":5.0,\"memoryGb\":4321.0,\"diskGb\":1234.0,\"bandwidthGbps\":0.3,\"diskSpeed\":\"slow\",\"storageType\":\"local\",\"architecture\":\"x86_64\"}";
        // Test adding new node with resources
        tester.assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                                          ("[{\"hostname\":\"" + hostname + "\"," + createIpAddresses("::1") + "\"id\":\"osid-123\"," +
                                           resources.replace("\"memoryGb\":4321.0,", "") + "}]").getBytes(StandardCharsets.UTF_8),
                                          Request.Method.POST),
                              400,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Required field 'memoryGb' is missing\"}");

        assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                        ("[{\"hostname\":\"" + hostname + "\"," + createIpAddresses("::1") + "\"id\":\"osid-123\"," + resources + "}]")
                                .getBytes(StandardCharsets.UTF_8),
                        Request.Method.POST),
                "{\"message\":\"Added 1 nodes to the provisioned state\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/" + hostname), resources);

        // Test patching with overrides
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/" + hostname,
                        "{\"diskGb\":12,\"memoryGb\":34,\"vcpu\":56,\"fastDisk\":true,\"remoteStorage\":true,\"bandwidthGbps\":78.0}".getBytes(StandardCharsets.UTF_8),
                        Request.Method.PATCH),
                "{\"message\":\"Updated " + hostname + "\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/" + hostname),
                                      "\"resources\":{\"vcpu\":56.0,\"memoryGb\":34.0,\"diskGb\":12.0,\"bandwidthGbps\":78.0,\"diskSpeed\":\"fast\",\"storageType\":\"remote\",\"architecture\":\"x86_64\"}");
    }

    @Test
    public void test_node_switch_hostname() throws Exception {
        String hostname = "host42.yahoo.com";
        // Add host with switch hostname
        String json = asNodeJson(hostname, NodeType.host, "default", Optional.empty(), Optional.empty(),
                Optional.of("switch0"), List.of(), List.of(), "127.0.42.1", "::42:1");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                                   ("[" + json + "]").getBytes(StandardCharsets.UTF_8),
                                   Request.Method.POST),
                       "{\"message\":\"Added 1 nodes to the provisioned state\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/" + hostname), "\"switchHostname\":\"switch0\"");

        // Update switch hostname
        json = "{\"switchHostname\":\"switch1\"}";
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/" + hostname, json.getBytes(StandardCharsets.UTF_8), Request.Method.PATCH),
                       "{\"message\":\"Updated host42.yahoo.com\"}");
        tester.assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/" + hostname), "\"switchHostname\":\"switch1\"");

        // Clear switch hostname
        json = "{\"switchHostname\":null}";
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/" + hostname, json.getBytes(StandardCharsets.UTF_8), Request.Method.PATCH),
                       "{\"message\":\"Updated host42.yahoo.com\"}");
        tester.assertPartialResponse(new Request("http://localhost:8080/nodes/v2/node/" + hostname), "switchHostname", false);
    }

    @Test
    public void exclusive_to_patch() throws IOException {
        String url = "http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com";
        tester.assertPartialResponse(new Request(url), "exclusiveTo", false); // Initially there is no exclusiveTo

        assertResponse(new Request(url, Utf8.toBytes("{\"exclusiveTo\": \"t1:a1:i1\"}"), Request.Method.PATCH),
                "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        tester.assertPartialResponse(new Request(url), "\"exclusiveTo\":\"t1:a1:i1\",", true);

        assertResponse(new Request(url, Utf8.toBytes("{\"provisionedFor\": \"t1:a1:i1\"}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        tester.assertPartialResponse(new Request(url), "\"provisionedFor\":\"t1:a1:i1\",", true);

        assertResponse(new Request(url, Utf8.toBytes("{\"hostTTL\": 86400000}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        tester.assertPartialResponse(new Request(url), "\"hostTTL\":86400000", true);

        assertResponse(new Request(url, Utf8.toBytes("{\"hostEmptyAt\": 789}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        tester.assertPartialResponse(new Request(url), "\"hostEmptyAt\":789", true);

        assertResponse(new Request(url, Utf8.toBytes("{\"exclusiveToClusterType\": \"admin\"}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        tester.assertPartialResponse(new Request(url), "\"exclusiveToClusterType\":\"admin\",", true);

        tester.assertResponse(new Request(url, Utf8.toBytes("{\"exclusiveTo\": \"t1:a1:i2\"}"), Request.Method.PATCH),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'exclusiveTo': exclusiveToApplicationId must be the same as provisionedForApplicationId when this is set\"}");

        assertResponse(new Request(url, Utf8.toBytes("{\"provisionedFor\": null}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        assertResponse(new Request(url, Utf8.toBytes("{\"exclusiveTo\": null, \"hostTTL\":null, \"hostEmptyAt\":null, \"exclusiveToClusterType\": null}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");

        tester.assertPartialResponse(new Request(url), "\"exclusiveTo", false);
        tester.assertPartialResponse(new Request(url), "\"hostTTL\"", false);
        tester.assertPartialResponse(new Request(url), "\"hostEmptyAt\"", false);
    }

    @Test
    public void trusted_certificates_patch()  throws IOException {
        String url = "http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com";
        tester.assertPartialResponse(new Request(url), "\"trustStore\":[]", false); // initially empty list

        String trustStore = "\"trustStore\":[" +
                            "{" +
                            "\"fingerprint\":\"foo\"," +
                            "\"expiry\":1632302251000" +
                            "}," +
                            "{" +
                            "\"fingerprint\":\"bar\"," +
                            "\"expiry\":1758532706000" +
                            "}" +
                            "]";
        assertResponse(new Request(url, Utf8.toBytes("{"+trustStore+"}"), Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        tester.assertPartialResponse(new Request(url), trustStore, true);
    }

    private static String asDockerNodeJson(String hostname, String parentHostname, String... ipAddress) {
        return asDockerNodeJson(hostname, NodeType.tenant, parentHostname, ipAddress);
    }

    private static String asDockerNodeJson(String hostname, NodeType nodeType, String parentHostname, String... ipAddress) {
        return "{\"hostname\":\"" + hostname + "\", \"parentHostname\":\"" + parentHostname + "\"," +
               createIpAddresses(ipAddress) +
               "\"id\":\"" + hostname + "\",\"flavor\":\"d-1-1-100\"" +
               (nodeType != NodeType.tenant ? ",\"type\":\"" + nodeType +  "\"" : "") +
               "}";
    }

    private static String asNodeJson(String hostname, String flavor, String... ipAddress) {
        return "{\"hostname\":\"" + hostname + "\", \"id\":\"" + hostname + "\"," +
                createIpAddresses(ipAddress) +
                "\"flavor\":\"" + flavor + "\"}";
    }

    private static String asHostJson(String hostname, String flavor, List<String> additionalIpAddresses, List<String> additionalHostnames, String... ipAddress) {
        return asNodeJson(hostname, NodeType.host, flavor, Optional.empty(), Optional.empty(), Optional.empty(),
                additionalIpAddresses, additionalHostnames, ipAddress);
    }

    private static String asNodeJson(String hostname, NodeType nodeType, String flavor, Optional<TenantName> reservedTo,
                                     Optional<ApplicationId> exclusiveTo, Optional<String> switchHostname,
                                     List<String> additionalIpAddresses, List<String> additionalHostnames, String... ipAddress) {
        return "{\"hostname\":\"" + hostname + "\", \"id\":\"" + hostname + "\"," +
               createIpAddresses(ipAddress) +
               "\"flavor\":\"" + flavor + "\"" +
               (reservedTo.map(tenantName -> ", \"reservedTo\":\"" + tenantName.value() + "\"").orElse("")) +
               (exclusiveTo.map(appId -> ", \"provisionedFor\":\"" + appId.serializedForm() + "\"").orElse("")) +
               (switchHostname.map(s -> ", \"switchHostname\":\"" + s + "\"").orElse("")) +
               (additionalIpAddresses.isEmpty() ? "" : ", \"additionalIpAddresses\":[\"" + String.join("\",\"", additionalIpAddresses) + "\"]") +
               (additionalHostnames.isEmpty() ? "" : ", \"additionalHostnames\":[\"" + String.join("\",\"", additionalHostnames) + "\"]") +
               ", \"type\":\"" + nodeType + "\"}";
    }

    private static String createIpAddresses(String... ipAddress) {
        return "\"ipAddresses\":[" +
                Arrays.stream(ipAddress)
                      .map(ip -> "\"" + ip + "\"")
                      .collect(Collectors.joining(",")) +
                "],";
    }

    private void assertRestart(int restartCount, Request request) throws IOException {
        tester.assertResponse(request, 200, "{\"message\":\"Scheduled restart of " + restartCount + " matching nodes\"}");
    }

    private void assertReboot(int rebootCount, Request request) throws IOException {
        tester.assertResponse(request, 200, "{\"message\":\"Scheduled reboot of " + rebootCount + " matching nodes\"}");
    }

    private void assertFile(Request request, String file) throws IOException {
        tester.assertFile(request, file);
    }

    private void assertResponse(Request request, String response) throws IOException {
        tester.assertResponse(request, response);
    }

}
