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
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test of the nodes/v2 API.
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
        container.close();
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
        assertRestart(9, new Request("http://localhost:8080/nodes/v2/command/restart",
                         new byte[0], Request.Method.POST));
        assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host2.yahoo.com"),
                               "\"restartGeneration\":3");

        // POST reboot command
        assertReboot(10, new Request("http://localhost:8080/nodes/v2/command/reboot?state=failed%20active",
                        new byte[0], Request.Method.POST));
        assertReboot(2, new Request("http://localhost:8080/nodes/v2/command/reboot?application=tenant2.application2.instance2",
                        new byte[0], Request.Method.POST));
        assertReboot(15, new Request("http://localhost:8080/nodes/v2/command/reboot",
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
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/availablefornewallocations/test-container-1",
                        new byte[0], Request.Method.PUT),
                "{\"message\":\"Marked following nodes as available for new allocation: test-container-1\"}");

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
                                   Utf8.toBytes("{\"flavor\": \"medium-disk\"}"), Request.Method.PATCH),
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
                                   Utf8.toBytes("{\"currentDockerImage\": \"ignored-image-name:4443/vespa/ci:6.43.0\"}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");

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
        assertFile(new Request("http://localhost:8080/nodes/v2/acl/cfg1"), "acl-config-server.json");
    }

    @Test
    public void acl_request_by_docker_host() throws Exception {
        assertFile(new Request("http://localhost:8080/nodes/v2/acl/dockerhost1.yahoo.com"), "acl-docker-host.json");
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
                        "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Can not set failed node host1.yahoo.com allocated to tenant1.application1.instance1 as 'container/id1/0/0' ready. It is not dirty.\"}");

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
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Can not set parked node host2.yahoo.com allocated to tenant2.application2.instance2 as 'content/id2/0/0' ready. It is not dirty.\"}");
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
        assertHardwareFailure(new Request("http://localhost:8080/nodes/v2/node/host5.yahoo.com"), Optional.of(false));
        assertHardwareFailure(new Request("http://localhost:8080/nodes/v2/node/dockerhost2.yahoo.com"), Optional.of(false));

        assertResponse(new Request("http://localhost:8080/nodes/v2/node/dockerhost2.yahoo.com",
                        Utf8.toBytes("{" +
                                "\"hardwareFailureDescription\": \"memory_mcelog\"" +
                                "}"
                        ),
                        Request.Method.PATCH),
                "{\"message\":\"Updated dockerhost2.yahoo.com\"}");

        assertHardwareFailure(new Request("http://localhost:8080/nodes/v2/node/host5.yahoo.com"), Optional.of(true));
        assertHardwareFailure(new Request("http://localhost:8080/nodes/v2/node/dockerhost2.yahoo.com"), Optional.of(true));
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

    /** Tests the rendering of each node separately to make it easier to find errors */
    @Test
    public void test_single_node_rendering() throws Exception {
        for (int i = 1; i <= 10; i++) {
            if (i == 8 || i == 9) continue; // these nodes are added later
            assertFile(new Request("http://localhost:8080/nodes/v2/node/host" + i + ".yahoo.com"), "node" + i + ".json");
        }
    }

    private String asDockerNodeJson(String hostname, String parentHostname, int additionalIpCount, String... ipAddress) {
        return "{\"hostname\":\"" + hostname + "\", \"parentHostname\":\"" + parentHostname + "\"," +
                createIpAddresses(ipAddress) +
                createAdditionalIpAddresses(additionalIpCount) +
                "\"openStackId\":\"" + hostname + "\",\"flavor\":\"docker\"}";
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

    private Optional<Boolean> getHardwareFailure(String json) {
        Slime slime = SlimeUtils.jsonToSlime(json.getBytes());
        Cursor hardwareFailure = slime.get().field("hardwareFailure");
        if (!hardwareFailure.valid()) {
            return Optional.empty();
        }

        return Optional.of(hardwareFailure.asBool());
    }

    private void assertHardwareFailure(Request request, Optional<Boolean> expectedHardwareFailure) throws CharacterCodingException {
        Response response = container.handleRequest(request);
        String json = response.getBodyAsString();
        Optional<Boolean> actualHardwareFailure = getHardwareFailure(json);
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
