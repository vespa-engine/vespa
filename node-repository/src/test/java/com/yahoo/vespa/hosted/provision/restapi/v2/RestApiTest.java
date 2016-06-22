// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.io.IOUtils;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.hosted.provision.testutils.ContainerConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class RestApiTest {

    private final static String responsesPath = "src/test/java/com/yahoo/vespa/hosted/provision/restapi/v2/responses/";

    /** This test gives examples of all the requests that can be made to nodes/v2 */
    @Test
    public void testRequests() throws Exception {
        // GET
        assertFile(new Request("http://localhost:8080/nodes/v2/"), "root.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/state/"), "states.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/state/?recursive=true"), "states-recursive.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/state/active?recursive=true"), "active-nodes.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/"), "nodes.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true"), "nodes-recursive.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host2.yahoo.com"), "node2.json");

        // GET with filters
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true&hostname=host3.yahoo.com%20host6.yahoo.com"), "application2-nodes.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true&clusterType=content"), "active-nodes.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true&clusterId=id2"), "application2-nodes.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true&application=tenant2.application2.instance2"), "application2-nodes.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/?recursive=true&parentHost=parent.yahoo.com,parent.host.yahoo.com"), "parent-nodes.json");

        // POST restart command
        assertRestart(1, new Request("http://localhost:8080/nodes/v2/command/restart?hostname=host3.yahoo.com",
                         new byte[0], Request.Method.POST));
        assertRestart(2, new Request("http://localhost:8080/nodes/v2/command/restart?application=tenant2.application2.instance2",
                         new byte[0], Request.Method.POST));
        assertRestart(4, new Request("http://localhost:8080/nodes/v2/command/restart",
                         new byte[0], Request.Method.POST));
        assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host3.yahoo.com"),
                               "\"restartGeneration\":3");

        // POST reboot command
        assertReboot(5, new Request("http://localhost:8080/nodes/v2/command/reboot?state=failed%20active",
                        new byte[0], Request.Method.POST));
        assertReboot(2, new Request("http://localhost:8080/nodes/v2/command/reboot?application=tenant2.application2.instance2",
                        new byte[0], Request.Method.POST));
        assertReboot(9, new Request("http://localhost:8080/nodes/v2/command/reboot",
                        new byte[0], Request.Method.POST));
        assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host3.yahoo.com"),
                               "\"rebootGeneration\":3");

        // POST new nodes
        assertResponse(new Request("http://localhost:8080/nodes/v2/node",
                                   ("[" + asNodeJson("host8.yahoo.com", "default") + "," +
                                           asNodeJson("host9.yahoo.com", "large-variant") + "," +
                                           asHostJson("parent2.yahoo.com", "large-variant") + "," +
                                           asDockerNodeJson("host11.yahoo.com", "parent.host.yahoo.com") + "]").
                                   getBytes(StandardCharsets.UTF_8),
                                   Request.Method.POST),
                        "{\"message\":\"Added 4 nodes to the provisioned state\"}");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host8.yahoo.com"), "node8.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host9.yahoo.com"), "node9.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/host11.yahoo.com"), "node11.json");
        assertFile(new Request("http://localhost:8080/nodes/v2/node/parent2.yahoo.com"), "parent2.json");

        // PUT nodes ready
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/host8.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host8.yahoo.com to ready\"}");
        assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host8.yahoo.com"),
                                           "\"state\":\"ready\"");
        // calling ready again is a noop:
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/ready/host8.yahoo.com",
                                  new byte[0], Request.Method.PUT),
                       "{\"message\":\"Nothing done; host8.yahoo.com is already ready\"}");

        // PUT a node in failed ...
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed/host8.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host8.yahoo.com to failed\"}");
        assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host8.yahoo.com"),
                               "\"state\":\"failed\"");
        // ... and put it back in active (after fixing). This is useful to restore data when multiple nodes fail.
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/active/host8.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host8.yahoo.com to active\"}");

        // PUT a node in failed ...
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed/host8.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host8.yahoo.com to failed\"}");
        assertResponseContains(new Request("http://localhost:8080()/nodes/v2/node/host8.yahoo.com"),
                               "\"state\":\"failed\"");
        // ... and delete it
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host8.yahoo.com",
                                   new byte[0], Request.Method.DELETE),
                       "{\"message\":\"Removed host8.yahoo.com\"}");

        // or, PUT a node in failed ...
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/failed/host6.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host6.yahoo.com to failed\"}");
        assertResponseContains(new Request("http://localhost:8080/nodes/v2/node/host6.yahoo.com"),
                                           "\"state\":\"failed\"");
        // ... and deallocate it such that it moves to dirty and is recycled
        assertResponse(new Request("http://localhost:8080/nodes/v2/state/dirty/host6.yahoo.com",
                                   new byte[0], Request.Method.PUT),
                       "{\"message\":\"Moved host6.yahoo.com to dirty\"}");

        // Update (PATCH) a node (multiple fields can also be sent in one request body)
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"currentRestartGeneration\": 1}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com/currentRebootGeneration",
                                   Utf8.toBytes("{\"currentRebootGeneration\": 1}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"flavor\": \"medium-disk\"}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"currentVespaVersion\": \"5.104.142\"}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                        Utf8.toBytes("{\"currentHostedVersion\": \"2.1.2408\"}"), Request.Method.PATCH),
                "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                        Utf8.toBytes("{\"convergedStateVersion\": \"5.104.142-2.1.2408\"}"), Request.Method.PATCH),
                "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"hardwareFailure\": true}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"parentHostname\": \"parent.yahoo.com\"}"), Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");

        assertFile(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com"), "node4-after-changes.json");
    }

    @Test
    public void testInvalidRequests() throws IOException {
        // Attempt to DELETE a node which is not put in failed first
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host8.yahoo.com",
                                   new byte[0], Request.Method.DELETE),
                       404, "{\"error-code\":\"NOT_FOUND\",\"message\":\"No node in the failed state with hostname host8.yahoo.com\"}");

        // PUT current restart generation with string instead of long
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"currentRestartGeneration\": \"1\"}"), Request.Method.PATCH),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'currentRestartGeneration': Expected a LONG value, got a STRING\"}");

        // PUT flavor with long instead of string
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{\"flavor\": 1}"), Request.Method.PATCH),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'flavor': Expected a STRING value, got a LONG\"}");
    }


    @Test
    public void testNodePatching() throws IOException {
        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host4.yahoo.com",
                                   Utf8.toBytes("{" +
                                           "\"currentRestartGeneration\": 1," +
                                           "\"currentRebootGeneration\": 3," +
                                           "\"flavor\": \"medium-disk\"," +
                                           "\"currentVespaVersion\": \"5.104.142\"," +
                                           "\"currentHostedVersion\": \"2.1.2408\"," +
                                           "\"hardwareFailure\": true," +
                                           "\"failCount\": 0," +
                                           "\"parentHostname\": \"parent.yahoo.com\"" +
                                       "}"
                                   ),
                                   Request.Method.PATCH),
                       "{\"message\":\"Updated host4.yahoo.com\"}");

        assertResponse(new Request("http://localhost:8080/nodes/v2/node/host5.yahoo.com",
                                   Utf8.toBytes("{\"currentRestartGeneration\": 1}"),
                                   Request.Method.PATCH),
                       400, "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Could not set field 'currentRestartGeneration': Node is not allocated\"}");
    }

    /** Tests the rendering of each node separately to make it easier to find errors */
    @Test
    public void testSingleNodeRendering() throws IOException {
        for (int i = 1; i <= 10; i++) {
            if (i == 8 || i == 9) continue; // these nodes are added later
            assertFile(new Request("http://localhost:8080/nodes/v2/node/host" + i + ".yahoo.com"), "node" + i + ".json");
        }
    }

    private JDisc container;
    @Before
    public void startContainer() { container = JDisc.fromServicesXml(ContainerConfig.servicesXmlV2(0), Networking.disable); }
    @After
    public void stopContainer() { container.close(); }

    private String asDockerNodeJson(String hostname, String parentHostname) {
        return "{\"hostname\":\"" + hostname + "\", \"parentHostname\":\"" + parentHostname +
                "\", \"openStackId\":\"" + hostname + "\",\"flavor\":\"docker\"}";
    }

    private String asNodeJson(String hostname, String flavor) {
        return "{\"hostname\":\"" + hostname + "\", \"openStackId\":\"" + hostname + "\",\"flavor\":\"" + flavor + "\"}";
    }

    private String asHostJson(String hostname, String flavor) {
        return "{\"hostname\":\"" + hostname + "\", \"openStackId\":\"" + hostname + "\",\"flavor\":\"" + flavor + "\"" +
                ", \"type\":\"host\"}";
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
        assertTrue("Response contains " + responseSnippet,
                   container.handleRequest(request).getBodyAsString().contains(responseSnippet));
    }

    private void assertFile(Request request, String responseFile) throws IOException {
        String expectedResponse = IOUtils.readFile(new File(responsesPath + responseFile));
        expectedResponse = include(expectedResponse);
        expectedResponse = expectedResponse.replaceAll("\\s", "");
        String responseString = container.handleRequest(request).getBodyAsString();
        assertEquals(responseFile, expectedResponse, responseString);
    }

    private void assertRestart(int restartCount, Request request) throws IOException {
        assertResponse(request, 200, "{\"message\":\"Scheduled restart of " + restartCount + " matching nodes\"}");
    }

    private void assertReboot(int rebootCount, Request request) throws IOException {
        assertResponse(request, 200, "{\"message\":\"Scheduled reboot of " + rebootCount + " matching nodes\"}");
    }

    /** Replace @include(localFile) with the content of the file */
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
