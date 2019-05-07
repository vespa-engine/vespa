// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.io.IOUtils;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.provision.testutils.ContainerConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.ComparisonFailure;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test of the REST APIs provided by the node repository.
 * 
 * Note: This class is referenced from our operations documentation and must not be renamed/moved without updating that.
 * 
 * @author bratseth
 */
public class RestApiTest {

    private final static String responsesPath = "src/test/java/com/yahoo/vespa/hosted/provision/restapi/v2/responses/";

    private JDisc container;

    @Before
    public void startContainer() {
        container = JDisc.fromServicesXml(ContainerConfig.servicesXmlV2(0), Networking.disable);
    }

    @After
    public void stopContainer() {
        if (container != null) container.close();
    }

    /** This test gives examples of all the requests that can be made to nodes/v2 */
    @Test
    public void test_requests() throws Exception {
        // GET
        assertFile(new Request("http://localhost:8080/nodes/v2/"), "root.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/state/"), "states.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/state/?recursive=true"), "states-recursive.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/state/active?recursive=true"), "active-nodes.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/"), "nodes.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true"), "nodes-recursive.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host2.yahoo.com"), "node2.json");

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
        assertRestart(11, new Request("http://localhost:8080/nodes/v2/command/restart",
                         new byte[0], Request.Method.POST));
        assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host2.yahoo.com"),
                               "\"restartGeneration\":3");

        // POST reboot command
        assertReboot(12, new Request("http://localhost:8080/nodes/v2/command/reboot?state=failed%20active",
                        new byte[0], Request.Method.POST));
        assertReboot(2, new Request("http://localhost:8080/nodes/v2/command/reboot?application=tenant2.application2.instance2",
                        new byte[0], Request.Method.POST));
        assertReboot(19, new Request("http://localhost:8080/nodes/v2/command/reboot",
                        new byte[0], Request.Method.POST));
        assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host2.yahoo.com"),
                               "\"rebootGeneration\":4");

        // POST deactivation of a maintenance job
        assertResponse(new Request("http://localhost:8080/nodes/v2/maintenance/inactive/NodeFailer",
                                   new byte[0], Request.Method.POST),
                       "{\"message\":\"Deactivated job 'NodeFailer'\"}");
        // GET a list of all maintenance jobs
        assertFile(new Request("http://localhost:8080/nodes/v2/maintenance/"), "maintenance.json");
        // DELETE deactivation of a maintenance job
        assertResponse(new Request("http://localhost:8080/nodes/v2/maintenance/inactive/NodeFailer",
                                   new byte[0], Request.Method.DELETE),
                       "{\"message\":\"Re-activated job 'NodeFailer'\"}");

        // POST new nodes
        assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                                   ("[" + asNodeJson("host8.yahoo.com", "default", "127.0.0.1") + "," + // test with only 1 ip address
                                          asNodeJson("host9.yahoo.com", "large-variant", "127.0.0.1", "::1") + "," +
                                          asHostJson("parent2.yahoo.com", "large-variant", "127.0.0.1", "::1") + "," +
                                          asDockerNodeJson("host11.yahoo.com", "parent.host.yahoo.com", 2, "127.0.0.1", "::1") + "]").
                                   getBytes(StandardCharsets.UTF_8),
                                   Request.Method.POST),
                        "{\"message\":\"Added 4 nodes to the provisioned state\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host8.yahoo.com"), "node8.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host9.yahoo.com"), "node9.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host11.yahoo.com"), "node11.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/parent2.yahoo.com"), "parent2.json");

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
        assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host8.yahoo.com"),
                                           "\"state\":\"ready\"");
        // calling ready again is a noop:
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/host8.yahoo.com",
                                  new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host8.yahoo.com to ready\"}");

        // PUT a node in failed ...
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed/host2.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host2.yahoo.com to failed\"}");
        assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host2.yahoo.com"),
                               "\"state\":\"failed\"");
        // ... and put it back in active (after fixing). This is useful to restore data when multiple nodes fail.
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/active/host2.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host2.yahoo.com to active\"}");

        // PUT a node in parked ...
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/parked/host8.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host8.yahoo.com to parked\"}");
        assertResponseContains(new Request("http://localhost:8080()/nodes/v2/node/host8.yahoo.com"),
                               "\"state\":\"parked\"");
        // ... and delete it
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host8.yahoo.com",
                                   new byte[0], Request.Method.DELETE),
                       "{\"message\":\"Removed host8.yahoo.com\"}");

        // or, PUT a node in failed ...
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed/test-container-1",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved test-container-1 to failed\"}");
        assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/test-container-1"),
                                           "\"state\":\"failed\"");
        // ... and deallocate it such that it moves to dirty and is recycled
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/dirty/test-container-1",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved test-container-1 to dirty\"}");

        // ... and set it back to ready as if this was from the node-admin with the temporary state rest api
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/test-container-1",
                        new byte[0], Request.Method.PUT),
                "{\"message\":\"Moved test-container-1 to ready\"}");

        assertResponse(new Request("http://localhost:8080/nodes/v2/node/test-container-1",  new byte[0], Request.Method.GET),
                404, "{\"error-code\":\"NOT_FOUND\",\"message\":\"No node with hostname 'test-container-1'\"}");

        // Put a host in failed and make sure it's children are also failed
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed/dockerhost1.yahoo.com", new byte[0], Request.Method.PUT),
                "{\"message\":\"Moved dockerhost1.yahoo.com, host4.yahoo.com to failed\"}");

        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed"), "{\"nodes\":[" +
                "{\"url\":\"http://localhost:8080/nodes/v2/node/host5.yahoo.com\"}," +
                "{\"url\":\"http://localhost:8080/nodes/v2/node/host4.yahoo.com\"}," +
                "{\"url\":\"http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com\"}]}");

        // Update (PATCH) a node (multiple fields can also be sent in one request body)
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"currentRestartGeneration\": 1}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
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
                                   Utf8.toBytes("{\"hardwareFailureDescription\": \"memory_mcelog\"}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"parentHostname\": \"parent.yahoo.com\"}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                        Utf8.toBytes("{\"ipAddresses\": [\"127.0.0.1\",\"::1\"]}"), Request.Method.PATCH),
                "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"wantToRetire\": true}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                        Utf8.toBytes("{\"wantToDeprovision\": true}"), Request.Method.PATCH),
                "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                        Utf8.toBytes("{\"currentVespaVersion\": \"6.43.0\",\"currentDockerImage\": \"docker-registry.domain.tld:8080/dist/vespa:6.45.0\"}"), Request.Method.PATCH),
                        "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                        Utf8.toBytes("{\"openStackId\": \"patched-openstackid\"}"), Request.Method.PATCH),
                "{\"message\":\"Updated host4.yahoo.com\"}");
        container.handleRequest((new Request("http://localhost:8080/nodes/v2/upgrade/tenant", Utf8.toBytes("{\"dockerImage\": \"docker.domain.tld/my/image\"}"), Request.Method.PATCH)));

        assertFile(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"), "node4-after-changes.json");
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
        assertResponse(req, 400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Illegal X-HTTP-Method-Override header for POST request. Accepts 'PATCH' but got 'GET'\"}");
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
                        ("[" + asNodeJson("dual-stack-host.yahoo.com", "default", "127.0.0.1", "::1") + "]").
                                getBytes(StandardCharsets.UTF_8),
                        Request.Method.POST),
                "{\"message\":\"Added 1 nodes to the provisioned state\"}");
    }

    @Test
    public void post_node_with_invalid_ip_address() throws Exception {
        Request req = new Request("http://localhost:8080/nodes/v2/node",
                ("[" + asNodeJson("host-with-ip.yahoo.com", "default", "foo") + "]").
                        getBytes(StandardCharsets.UTF_8),
                Request.Method.POST);
        assertResponse(req, 400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"A node must have at least one valid IP address: 'foo' is not an IP string literal.\"}");
    }

    @Test
    public void post_controller_node() throws Exception {
        String data = "[{\"hostname\":\"controller1.yahoo.com\", \"openStackId\":\"fake-controller1.yahoo.com\"," +
                      createIpAddresses("127.0.0.1") +
                      "\"flavor\":\"default\"" +
                      ", \"type\":\"controller\"}]";
        assertResponse(new Request("http://localhost:8080/nodes/v2/node", data.getBytes(StandardCharsets.UTF_8),
                                   Request.Method.POST),
                       "{\"message\":\"Added 1 nodes to the provisioned state\"}");

        assertFile(new Request("http://localhost:8080/nodes/v2/node/controller1.yahoo.com"), "controller1.json");
    }

    @Test
    public void fails_to_deallocate_node_with_hardware_failure() throws Exception {
        assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                        ("[" + asNodeJson("host12.yahoo.com", "default") + "]").
                                getBytes(StandardCharsets.UTF_8),
                        Request.Method.POST),
                "{\"message\":\"Added 1 nodes to the provisioned state\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host12.yahoo.com",
                        Utf8.toBytes("{\"hardwareFailureDescription\": \"memory_mcelog\"}"),
                        Request.Method.PATCH),
                "{\"message\":\"Updated host12.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed/host12.yahoo.com",
                        new byte[0], Request.Method.PUT),
                "{\"message\":\"Moved host12.yahoo.com to failed\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/dirty/host12.yahoo.com",
                new byte[0], Request.Method.PUT), 400,
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not deallocate host12.yahoo.com: It has a hardware failure\"}");
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
                "{\"message\":\"Moved foo.yahoo.com to failed\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/dirty/foo.yahoo.com",
                        new byte[0], Request.Method.PUT),
                "{\"message\":\"Moved foo.yahoo.com to dirty\"}");
        assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/foo.yahoo.com"),
                "\"rebootGeneration\":1");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/foo.yahoo.com",
                        Utf8.toBytes("{\"currentRebootGeneration\": 42}"), Request.Method.PATCH),
                "{\"message\":\"Updated foo.yahoo.com\"}");
        assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/foo.yahoo.com"),
                "\"rebootGeneration\":1");
    }

    @Test
    public void setting_node_to_ready_will_reset_certain_fields() throws Exception {
        final String hostname = "host55.yahoo.com";
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/" + hostname,
                        new byte[0], Request.Method.PUT),
                "{\"message\":\"Moved " + hostname + " to ready\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/" + hostname), "node55-after-changes.json");
    }

    @Test
    public void acl_request_by_tenant_node() throws Exception {
        String hostname = "foo.yahoo.com";
        assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                        ("[" + asNodeJson(hostname, "default") + "]").
                                getBytes(StandardCharsets.UTF_8),
                        Request.Method.POST),
                "{\"message\":\"Added 1 nodes to the provisioned state\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/dirty/" + hostname,
                        new byte[0], Request.Method.PUT),
                "{\"message\":\"Moved foo.yahoo.com to dirty\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/" + hostname,
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved foo.yahoo.com to ready\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/acl/" + hostname), "acl-tenant-node.json");
    }

    @Test
    public void acl_request_by_config_server() throws Exception {
        assertFile(new Request("http://localhost:8080/nodes/v2/acl/cfg1.yahoo.com"), "acl-config-server.json");
    }

    @Test
    public void acl_request_by_docker_host() throws Exception {
        assertFile(new Request("http://localhost:8080/nodes/v2/acl/dockerhost1.yahoo.com?children=true"), "acl-docker-host.json");
    }

    @Test
    public void test_invalid_requests() throws Exception {
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/node-does-not-exist",
                                   new byte[0], Request.Method.GET),
                       404, "{\"error-code\":\"NOT_FOUND\",\"message\":\"No node with hostname 'node-does-not-exist'\"}");

        // Attempt to fail and ready an allocated node without going through dirty
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed/node-does-not-exist",
                                   new byte[0], Request.Method.PUT),
                       404, "{\"error-code\":\"NOT_FOUND\",\"message\":\"Could not move node-does-not-exist to failed: Node not found\"}");

        // Attempt to fail and ready an allocated node without going through dirty
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed/host1.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host1.yahoo.com to failed\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/host1.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       400,
                        "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Can not set failed node host1.yahoo.com allocated to tenant1.application1.instance1 as 'container/id1/0/0' ready. It is not provisioned or dirty.\"}");

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
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/host2.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Can not set parked node host2.yahoo.com allocated to tenant2.application2.instance2 as 'content/id2/0/0' ready. It is not provisioned or dirty.\"}");
        // (... while dirty then ready works (the ready move will be initiated by node maintenance))
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/dirty/host2.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host2.yahoo.com to dirty\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/host2.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host2.yahoo.com to ready\"}");

        // Attempt to DELETE a node which is not put in a deletable state first
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host2.yahoo.com",
                                   new byte[0], Request.Method.DELETE),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Failed to delete host2.yahoo.com: Node host2.yahoo.com can only be removed from following states: provisioned, failed, parked\"}");

        // Attempt to DELETE allocated node
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   new byte[0], Request.Method.DELETE),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Failed to delete host4.yahoo.com: Node is currently allocated and cannot be removed: allocated to tenant3.application3.instance3 as 'content/id3/0/0'\"}");

        // PUT current restart generation with string instead of long
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"currentRestartGeneration\": \"1\"}"), Request.Method.PATCH),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'currentRestartGeneration': Expected a LONG value, got a STRING\"}");

        // PUT flavor with long instead of string
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"flavor\": 1}"), Request.Method.PATCH),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'flavor': Expected a STRING value, got a LONG\"}");

        // Attempt to set unallocated node active
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/active/host2.yahoo.com",
                                   new byte[0], Request.Method.PUT), 400,
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set host2.yahoo.com active. It has no allocation.\"}");
    }

    @Test
    public void test_node_patching() throws Exception {
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{" +
                                           "\"currentRestartGeneration\": 1," +
                                           "\"currentRebootGeneration\": 3," +
                                           "\"flavor\": \"medium-disk\"," +
                                           "\"currentVespaVersion\": \"5.104.142\"," +
                                           "\"hardwareFailureDescription\": \"memory_mcelog\"," +
                                           "\"failCount\": 0," +
                                           "\"parentHostname\": \"parent.yahoo.com\"" +
                                       "}"
                                   ),
                                   Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");

        assertResponse(new Request("http://localhost:8080/nodes/v2/node/doesnotexist.yahoo.com",
                                   Utf8.toBytes("{\"currentRestartGeneration\": 1}"),
                                   Request.Method.PATCH),
                       404, "{\"error-code\":\"NOT_FOUND\",\"message\":\"No node found with hostname doesnotexist.yahoo.com\"}");

        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host5.yahoo.com",
                                   Utf8.toBytes("{\"currentRestartGeneration\": 1}"),
                                   Request.Method.PATCH),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'currentRestartGeneration': Node is not allocated\"}");
    }

    @Test
    public void test_hardware_patching_of_docker_host() throws Exception {
        assertHardwareFailure(new Request("http://localhost:8080/nodes/v2/node/host5.yahoo.com"), false);
        assertHardwareFailure(new Request("http://localhost:8080/nodes/v2/node/dockerhost2.yahoo.com"), false);

        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost2.yahoo.com",
                        Utf8.toBytes("{\"hardwareFailureDescription\": \"memory_mcelog\"}"),
                        Request.Method.PATCH),
                "{\"message\":\"Updated dockerhost2.yahoo.com\"}");

        assertHardwareFailure(new Request("http://localhost:8080/nodes/v2/node/host5.yahoo.com"), true);
        assertHardwareFailure(new Request("http://localhost:8080/nodes/v2/node/dockerhost2.yahoo.com"), true);

        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost2.yahoo.com",
                        Utf8.toBytes("{\"hardwareFailureDescription\": \"null\"}"),
                        Request.Method.PATCH),
                "{\"message\":\"Updated dockerhost2.yahoo.com\"}");

        assertHardwareFailure(new Request("http://localhost:8080/nodes/v2/node/host5.yahoo.com"), false);
        assertHardwareFailure(new Request("http://localhost:8080/nodes/v2/node/dockerhost2.yahoo.com"), false);
    }

    @Test
    public void test_disallow_setting_currentDockerImage_for_non_docker_node() throws IOException {
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host1.yahoo.com",
                        Utf8.toBytes("{\"currentDockerImage\": \"ignored-image-name:4443/vespa/ci:6.45.0\"}"), Request.Method.PATCH),
                400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'currentDockerImage': Docker image can only be set for docker containers\"}");
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
    public void test_hardware_divergence_patching() throws Exception {
        // Add report
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com",
                                   Utf8.toBytes("{\"hardwareDivergence\": \"{\\\"actualCpuCores\\\":2}\"}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Updated host6.yahoo.com\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com"), "node6-after-changes.json");

        // Empty report is rejected
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com",
                                   Utf8.toBytes("{\"hardwareDivergence\": \"\"}"),
                                   Request.Method.PATCH),
                       400,
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'hardwareDivergence': Hardware divergence must be non-empty, but was ''\"}");

        // Clear report
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com",
                                   Utf8.toBytes("{\"hardwareDivergence\": null}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Updated host6.yahoo.com\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com"), "node6.json");

        // Clear on quoted "null" report
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com",
                        Utf8.toBytes("{\"hardwareDivergence\": \"null\"}"),
                        Request.Method.PATCH),
                "{\"message\":\"Updated host6.yahoo.com\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com"), "node6.json");
    }

    @Test
    public void test_reports_patching() throws IOException {
        // Add report
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com",
                        Utf8.toBytes("{" +
                                "  \"reports\": {" +
                                "    \"actualCpuCores\": {" +
                                "      \"createdMillis\": 1, " +
                                "      \"description\": \"Actual number of CPU cores (2) differs from spec (4)\"," +
                                "      \"value\":2" +
                                "    }," +
                                "    \"diskSpace\": {" +
                                "      \"createdMillis\": 2, " +
                                "      \"description\": \"Actual disk space (2TB) differs from spec (3TB)\"," +
                                "      \"details\": {" +
                                "        \"inGib\": 3," +
                                "        \"disks\": [\"/dev/sda1\", \"/dev/sdb3\"]" +
                                "      }" +
                                "    }" +
                                "  }" +
                                "}"),
                        Request.Method.PATCH),
                "{\"message\":\"Updated host6.yahoo.com\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com"), "node6-reports.json");

        // Patching with an empty reports is no-op
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com",
                        Utf8.toBytes("{\"reports\": {}}"),
                        Request.Method.PATCH),
                200,
                "{\"message\":\"Updated host6.yahoo.com\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com"), "node6-reports.json");

        // Patching existing report overwrites
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com",
                        Utf8.toBytes("{" +
                                "  \"reports\": {" +
                                "    \"actualCpuCores\": {" +
                                "      \"createdMillis\": 3 " +
                                "    }" +
                                "  }" +
                                "}"),
                        Request.Method.PATCH),
                200,
                "{\"message\":\"Updated host6.yahoo.com\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com"), "node6-reports-2.json");

        // Clearing one report
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com",
                        Utf8.toBytes("{\"reports\": { \"diskSpace\": null } }"),
                        Request.Method.PATCH),
                "{\"message\":\"Updated host6.yahoo.com\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com"), "node6-reports-3.json");

        // Clearing all reports
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com",
                        Utf8.toBytes("{\"reports\": null }"),
                        Request.Method.PATCH),
                "{\"message\":\"Updated host6.yahoo.com\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com"), "node6.json");
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

        // Setting empty version fails
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                                   Utf8.toBytes("{\"version\": null}"),
                                   Request.Method.PATCH),
                       400,
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Invalid target version: 0.0.0\"}");

        // Setting version for unsupported node type fails
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/tenant",
                                   Utf8.toBytes("{\"version\": \"6.123.456\"}"),
                                   Request.Method.PATCH),
                       400,
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Cannot set version for type tenant\"}");

        // Omitting version field fails
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                                   Utf8.toBytes("{}"),
                                   Request.Method.PATCH),
                       400,
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"At least one of 'version', 'osVersion' or 'dockerImage' must be set\"}");

        // Downgrade without force fails
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
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
                       "{\"versions\":{\"config\":\"6.123.456\",\"confighost\":\"6.123.1\",\"controller\":\"6.123.456\"},\"osVersions\":{\"host\":\"7.5.2\",\"confighost\":\"7.5.2\"},\"dockerImages\":{}}");

        // Upgrade OS and Vespa together
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                                   Utf8.toBytes("{\"version\": \"6.124.42\", \"osVersion\": \"7.5.2\"}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Set version to 6.124.42, osVersion to 7.5.2 for nodes of type confighost\"}");

        // Attempt to upgrade unsupported node type
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/config",
                                   Utf8.toBytes("{\"osVersion\": \"7.5.2\"}"),
                                   Request.Method.PATCH),
                       400,
                       "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Setting target OS version for config nodes is unsupported\"}");

        // Attempt to downgrade OS
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
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
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                                   Utf8.toBytes("{\"osVersion\": null}"),
                                   Request.Method.PATCH),
                       200,
                       "{\"message\":\"Set osVersion to null for nodes of type confighost\"}");

        // Set docker image for config and tenant
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/tenant",
                        Utf8.toBytes("{\"dockerImage\": \"my-repo.my-domain.example:1234/repo/tenant\"}"),
                        Request.Method.PATCH),
                "{\"message\":\"Set docker image to my-repo.my-domain.example:1234/repo/tenant for nodes of type tenant\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/config",
                        Utf8.toBytes("{\"dockerImage\": \"my-repo.my-domain.example:1234/repo/image\"}"),
                        Request.Method.PATCH),
                "{\"message\":\"Set docker image to my-repo.my-domain.example:1234/repo/image for nodes of type config\"}");

        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/"),
                "{\"versions\":{\"config\":\"6.123.456\",\"confighost\":\"6.124.42\",\"controller\":\"6.123.456\"},\"osVersions\":{\"host\":\"7.5.2\"},\"dockerImages\":{\"tenant\":\"my-repo.my-domain.example:1234/repo/tenant\",\"config\":\"my-repo.my-domain.example:1234/repo/image\"}}");

        // Cannot set docker image for non docker node type
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/confighost",
                        Utf8.toBytes("{\"dockerImage\": \"my-repo.my-domain.example:1234/repo/image\"}"),
                        Request.Method.PATCH),
                400,
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Setting docker image for confighost nodes is unsupported\"}");
    }

    @Test
    public void test_os_version() throws Exception {
        // Schedule OS upgrade
        assertResponse(new Request("http://localhost:8080/nodes/v2/upgrade/host",
                                   Utf8.toBytes("{\"osVersion\": \"7.5.2\"}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Set osVersion to 7.5.2 for nodes of type host\"}");

        // Other node type does not return wanted OS version
        Response r = container.handleRequest(new Request("http://localhost:8080/nodes/v2/node/host1.yahoo.com"));
        assertFalse("Response omits wantedOsVersions field", r.getBodyAsString().contains("wantedOsVersion"));

        // Node updates its node object after upgrading OS
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com",
                                   Utf8.toBytes("{\"currentOsVersion\": \"7.5.2\"}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost1.yahoo.com\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com"), "docker-node1-os-upgrade-complete.json");

        // Another node upgrades
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost2.yahoo.com",
                                   Utf8.toBytes("{\"currentOsVersion\": \"7.5.2\"}"),
                                   Request.Method.PATCH),
                       "{\"message\":\"Updated dockerhost2.yahoo.com\"}");

        // Filter nodes by osVersion
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/?osVersion=7.5.2"),
                       "{\"nodes\":[" +
                       "{\"url\":\"http://localhost:8080/nodes/v2/node/dockerhost2.yahoo.com\"}," +
                       "{\"url\":\"http://localhost:8080/nodes/v2/node/dockerhost1.yahoo.com\"}" +
                       "]}");
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

    /** Tests the rendering of each node separately to make it easier to find errors */
    @Test
    public void test_single_node_rendering() throws Exception {
        for (int i = 1; i <= 14; i++) {
            if (i == 8 || i == 9 || i == 11 || i == 12) continue; // these nodes are added later
            assertFile(new Request("http://localhost:8080/nodes/v2/node/host" + i + ".yahoo.com"), "node" + i + ".json");
        }
    }

    @Test
    public void test_load_balancers() throws Exception {
        assertFile(new Request("http://localhost:8080/loadbalancers/v1/"), "load-balancers.json");
        assertFile(new Request("http://localhost:8080/loadbalancers/v1/?application=tenant4.application4.instance4"), "load-balancers.json");
        assertResponse(new Request("http://localhost:8080/loadbalancers/v1/?application=tenant.nonexistent.default"), "{\"loadBalancers\":[]}");
    }

    private String asDockerNodeJson(String hostname, String parentHostname, int additionalIpCount, String... ipAddress) {
        return "{\"hostname\":\"" + hostname + "\", \"parentHostname\":\"" + parentHostname + "\"," +
                createIpAddresses(ipAddress) +
                createAdditionalIpAddresses(additionalIpCount) +
                "\"openStackId\":\"" + hostname + "\",\"flavor\":\"d-1-1-100\"}";
    }

    private String asNodeJson(String hostname, String flavor, String... ipAddress) {
        return "{\"hostname\":\"" + hostname + "\", \"openStackId\":\"" + hostname + "\"," +
                createIpAddresses(ipAddress) +
                "\"flavor\":\"" + flavor + "\"}";
    }

    private String asHostJson(String hostname, String flavor, String... ipAddress) {
        return "{\"hostname\":\"" + hostname + "\", \"openStackId\":\"" + hostname + "\"," +
                createIpAddresses(ipAddress) +
                "\"flavor\":\"" + flavor + "\"" +
                ", \"type\":\"host\"}";
    }

    private String createAdditionalIpAddresses(int count) {
        return "\"additionalIpAddresses\":[" +
                IntStream.range(10, 10+count)
                        .mapToObj(i -> "\"::" + i + "\"")
                        .collect(Collectors.joining(",")) +
                "],";
    }

    private String createIpAddresses(String... ipAddress) {
        return "\"ipAddresses\":[" +
                Arrays.stream(ipAddress)
                      .map(ip -> "\"" + ip + "\"")
                      .collect(Collectors.joining(",")) +
                "],";
    }

    private boolean getHardwareFailure(String json) {
        Slime slime = SlimeUtils.jsonToSlime(json.getBytes());
        Cursor hardwareFailure = slime.get().field("hardwareFailure");
        if (!hardwareFailure.valid())
            throw new IllegalStateException("hardwareFailure is invalid");

        return hardwareFailure.asBool();
    }

    private void assertHardwareFailure(Request request, boolean expectedHardwareFailure) throws CharacterCodingException {
        Response response = container.handleRequest(request);
        String json = response.getBodyAsString();
        boolean actualHardwareFailure = getHardwareFailure(json);
        assertEquals(expectedHardwareFailure, actualHardwareFailure);
        assertEquals(200, response.getStatus());
    }

    /** Asserts a particular response and 200 as response status */
    private void assertResponse(Request request, String responseMessage) throws IOException {
        assertResponse(request, 200, responseMessage);
    }

    private void assertResponse(Request request, int responseStatus, String responseMessage) throws IOException {
        Response response = container.handleRequest(request);
        // Compare both status and message at once for easier diagnosis
        assertEquals("status: " + responseStatus + "\nmessage: " + responseMessage,
                     "status: " + response.getStatus() + "\nmessage: " + response.getBodyAsString());
    }

    private void assertResponseContains(Request request, String responseSnippet) throws IOException {
        String response = container.handleRequest(request).getBodyAsString();
        assertTrue(String.format("Expected response to contain: %s\nResponse: %s", responseSnippet, response),
                response.contains(responseSnippet));
    }

    private void assertFile(Request request, String responseFile) throws IOException {
        String expectedResponse = IOUtils.readFile(new File(responsesPath + responseFile));
        expectedResponse = include(expectedResponse);
        expectedResponse = expectedResponse.replaceAll("(\"[^\"]*\")|\\s*", "$1"); // Remove whitespace
        String responseString = container.handleRequest(request).getBodyAsString();
        if (expectedResponse.contains("(ignore)")) {
            // Convert expected response to a literal pattern and replace any ignored field with a pattern that matches
            // until the first stop character
            String stopCharacters = "[^,:\\\\[\\\\]{}]";
            String expectedResponsePattern = Pattern.quote(expectedResponse)
                                                    .replaceAll("\"?\\(ignore\\)\"?", "\\\\E" +
                                                                                      stopCharacters + "*\\\\Q");
            if (!Pattern.matches(expectedResponsePattern, responseString)) {
                throw new ComparisonFailure(responseFile + " (with ignored fields)", expectedResponsePattern,
                                            responseString);
            }
        } else {
            assertEquals(responseFile, expectedResponse, responseString);
        }
    }

    private void assertRestart(int restartCount, Request request) throws IOException {
        assertResponse(request, 200, "{\"message\":\"Scheduled restart of " + restartCount + " matching nodes\"}");
    }

    private void assertReboot(int rebootCount, Request request) throws IOException {
        assertResponse(request, 200, "{\"message\":\"Scheduled reboot of " + rebootCount + " matching nodes\"}");
    }

    /** Replaces @include(localFile) with the content of the file */
    private String include(String response) throws IOException {
        // Please don't look at this code
        int includeIndex = response.indexOf("@include(");
        if (includeIndex < 0) return response;
        String prefix = response.substring(0, includeIndex);
        String rest = response.substring(includeIndex + "@include(".length());
        int filenameEnd = rest.indexOf(")");
        String includeFileName = rest.substring(0, filenameEnd);
        String includedContent = IOUtils.readFile(new File(responsesPath + includeFileName));
        includedContent = include(includedContent);
        String postFix = rest.substring(filenameEnd + 1);
        postFix = include(postFix);
        return prefix + includedContent + postFix;
    }

}
