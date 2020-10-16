// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.yahoo.application.Application;
import com.yahoo.application.Networking;
import com.yahoo.container.Container;
import com.yahoo.jdisc.http.server.jetty.JettyHttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Tests the implementation of the orchestrator Application API.
 *
 * @author smorgrav
 */
public class ApplicationSuspensionResourceTest {

    private static final String BASE_PATH = "/orchestrator/v1/suspensions/applications";
    private static final String RESOURCE_1 = "mediasearch:imagesearch:default";
    private static final String RESOURCE_2 = "test-tenant-id:application:instance";
    private static final String INVALID_RESOURCE_NAME = "something_without_colons";

    private Application jdiscApplication;
    private WebTarget webTarget;

    @Before
    public void setup() throws Exception {
        jdiscApplication = Application.fromServicesXml(servicesXml(), Networking.enable);
        Client client = ClientBuilder.newClient();

        JettyHttpServer serverProvider = (JettyHttpServer) Container.get().getServerProviderRegistry().allComponents().get(0);
        String url = "http://localhost:" + serverProvider.getListenPort() + BASE_PATH;
        webTarget = client.target(new URI(url));
    }

    @After
    public void teardown() {
        jdiscApplication.close();
        webTarget = null;
    }

    @Ignore
    @Test
    public void run_application_locally_for_manual_browser_testing() throws Exception {
        System.out.println(webTarget.getUri());
        Thread.sleep(3600 * 1000);
    }

    @Test
    public void get_all_suspended_applications_return_empty_list_initially() {
        Response reply = webTarget.request().get();
        assertEquals(200, reply.getStatus());
        assertEquals("[]", reply.readEntity(String.class));
    }

    @Test
    public void invalid_application_id_throws_http_400() {
        Response reply = webTarget.request().post(Entity.entity(INVALID_RESOURCE_NAME, MediaType.APPLICATION_JSON_TYPE));
        assertEquals(400, reply.getStatus());
    }

    @Test
    public void get_application_status_returns_404_for_not_suspended_and_204_for_suspended() {
        // Get on application that is not suspended
        Response reply = webTarget.path(RESOURCE_1).request().get();
        assertEquals(404, reply.getStatus());

        // Post application
        reply = webTarget.request().post(Entity.entity(RESOURCE_1, MediaType.APPLICATION_JSON_TYPE));
        assertEquals(204, reply.getStatus());

        // Get on the application that now should be in suspended
        reply = webTarget.path(RESOURCE_1).request().get();
        assertEquals(204, reply.getStatus());
    }

    @Test
    public void delete_works_on_suspended_and_not_suspended_applications() {
        // Delete an application that is not suspended
        Response reply = webTarget.path(RESOURCE_1).request().delete();
        assertEquals(204, reply.getStatus());

        // Put application in suspend
        reply = webTarget.request().post(Entity.entity(RESOURCE_1, MediaType.APPLICATION_JSON_TYPE));
        assertEquals(204, reply.getStatus());

        // Check that it is in suspend
        reply = webTarget.path(RESOURCE_1).request(MediaType.APPLICATION_JSON).get();
        assertEquals(204, reply.getStatus());

        // Delete it
        reply = webTarget.path(RESOURCE_1).request().delete();
        assertEquals(204, reply.getStatus());

        // Check that it is not in suspend anymore
        reply = webTarget.path(RESOURCE_1).request(MediaType.APPLICATION_JSON).get();
        assertEquals(404, reply.getStatus());
    }

    @Test
    public void list_applications_returns_the_correct_list_of_suspended_applications() {
        // Test that initially we have the empty set
        Response reply = webTarget.request(MediaType.APPLICATION_JSON).get();
        assertEquals(200, reply.getStatus());
        assertEquals("[]", reply.readEntity(String.class));

        // Add a couple of applications to maintenance
        webTarget.request().post(Entity.entity(RESOURCE_1, MediaType.APPLICATION_JSON_TYPE));
        webTarget.request().post(Entity.entity(RESOURCE_2, MediaType.APPLICATION_JSON_TYPE));
        assertEquals(200, reply.getStatus());

        // Test that we get them back
        Set<String> responses = webTarget.request(MediaType.APPLICATION_JSON_TYPE)
                .get(new GenericType<Set<String>>() {});
        assertEquals(2, responses.size());

        // Remove suspend for the first resource
        webTarget.path(RESOURCE_1).request().delete();

        // Test that we are back to the start with the empty set
        responses = webTarget.request(MediaType.APPLICATION_JSON_TYPE)
                .get(new GenericType<Set<String>>() {});
        assertEquals(1, responses.size());
        assertEquals(RESOURCE_2, responses.iterator().next());
    }

    private String servicesXml() {
        return "<services>\n" +
                "    <container version=\"1.0\" jetty=\"true\">\n" +
                "        <accesslog type=\"disabled\"/>\n" +
                "        <config name=\"container.handler.threadpool\">\n" +
                "            <maxthreads>10</maxthreads>\n" +
                "        </config>\n" +
                "        <component id=\"com.yahoo.vespa.flags.InMemoryFlagSource\" bundle=\"flags\" />\n" +
                "        <component id=\"com.yahoo.vespa.curator.mock.MockCurator\" bundle=\"zkfacade\" />\n" +
                "        <component id=\"com.yahoo.vespa.orchestrator.status.ZkStatusService\" bundle=\"orchestrator\" />\n" +
                "        <component id=\"com.yahoo.vespa.orchestrator.DummyServiceMonitor\" bundle=\"orchestrator\" />\n" +
                "        <component id=\"com.yahoo.vespa.orchestrator.OrchestratorImpl\" bundle=\"orchestrator\" />\n" +
                "        <component id=\"com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactoryMock\" bundle=\"orchestrator\" />\n" +
                "\n" +
                "        <rest-api path=\"orchestrator\">\n" +
                "            <components bundle=\"orchestrator\" />\n" +
                "        </rest-api>\n" +
                "\n" +
                "        <http>\n" +
                "            <server id=\"foo\" port=\"0\"/>\n" +
                "        </http>\n" +
                "    </container>\n" +
                "</services>\n";
    }

}
