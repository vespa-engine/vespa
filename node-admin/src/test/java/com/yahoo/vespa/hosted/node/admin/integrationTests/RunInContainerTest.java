// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
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
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author dybis
 */
public class RunInContainerTest {

    private JDisc container;
    private int port;

    private int findRandomOpenPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Before
    public void startContainer() throws Exception {
        try {
            OrchestratorMock.semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        OrchestratorMock.reset();
        port = findRandomOpenPort();
        System.out.println("PORT IS " + port);
        container = JDisc.fromServicesXml(createServiceXml(port), Networking.enable);
    }

    @After
    public void after() {
        container.close();
        OrchestratorMock.semaphore.release();
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
                HttpEntity entity = httpResponse.getEntity();
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
        waitForJdiscContainerToServe();
        assertThat(doPutCall("resume"), is(true));
        OrchestratorMock.setForceGroupSuspendResponse(Optional.of("Denied"));
        assertThat(doPutCall("suspend"), is(false));
        assertThat(OrchestratorMock.getRequests(), is("Suspend with parent: localhost and hostnames: [] - Forced response: Optional[Denied]\n"));
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