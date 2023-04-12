// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequestBuilder;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.core.SystemTimer;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.restapi.RestApiTestDriver;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.orchestrator.DummyServiceMonitor;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.orchestrator.config.OrchestratorConfig;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactoryMock;
import com.yahoo.vespa.orchestrator.status.ZkStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static com.yahoo.jdisc.http.HttpRequest.Method;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the implementation of the orchestrator Application API.
 *
 * @author smorgrav
 * @author bjorncs
 */
class ApplicationSuspensionRequestHandlerTest {

    private static final String RESOURCE_1 = "mediasearch:imagesearch:default";
    private static final String RESOURCE_2 = "test-tenant-id:application:instance";
    private static final String INVALID_RESOURCE_NAME = "something_without_colons";

    private RestApiTestDriver testDriver;

    @BeforeEach
    void createHandler() {
        DummyServiceMonitor serviceMonitor = new DummyServiceMonitor();
        Orchestrator orchestrator = new OrchestratorImpl(
                new OrchestratorConfig(new OrchestratorConfig.Builder()), new ConfigserverConfig(new ConfigserverConfig.Builder()), new ClusterControllerClientFactoryMock(),
                new ZkStatusService(new MockCurator(), new MockMetric(), new SystemTimer(), serviceMonitor),
                serviceMonitor,
                new InMemoryFlagSource(),
                Zone.defaultZone());
        var handler = new ApplicationSuspensionRequestHandler(RestApiTestDriver.createHandlerTestContext(), orchestrator);
        testDriver = RestApiTestDriver.newBuilder(handler).build();
    }


    @Test
    void get_all_suspended_applications_return_empty_list_initially() {
        HttpResponse httpResponse = executeRequest(Method.GET, "", null);
        assertEquals(200, httpResponse.getStatus());
        Set<String> set = parseResponseContent(httpResponse, new TypeReference<>() {});
        assertEquals(0, set.size());
    }

    @Test
    void invalid_application_id_throws_http_400() {
        HttpResponse httpResponse = executeRequest(Method.POST, "", INVALID_RESOURCE_NAME);
        assertEquals(400, httpResponse.getStatus());
    }

    @Test
    void get_application_status_returns_404_for_not_suspended_and_204_for_suspended() {
        // Get on application that is not suspended
        HttpResponse httpResponse = executeRequest(Method.GET, "/"+RESOURCE_1, null);
        assertEquals(404, httpResponse.getStatus());

        // Post application
        httpResponse = executeRequest(Method.POST, "", RESOURCE_1);
        assertEquals(204, httpResponse.getStatus());

        // Get on the application that now should be in suspended
        httpResponse = executeRequest(Method.GET, "/"+RESOURCE_1, null);
        assertEquals(204, httpResponse.getStatus());
    }

    @Test
    void delete_works_on_suspended_and_not_suspended_applications() {
        // Delete an application that is not suspended
        HttpResponse httpResponse = executeRequest(Method.DELETE, "/"+RESOURCE_1, null);
        assertEquals(204, httpResponse.getStatus());

        // Put application in suspend
        httpResponse = executeRequest(Method.POST, "", RESOURCE_1);
        assertEquals(204, httpResponse.getStatus());

        // Check that it is in suspend
        httpResponse = executeRequest(Method.GET, "/"+RESOURCE_1, null);
        assertEquals(204, httpResponse.getStatus());

        // Delete it
        httpResponse = executeRequest(Method.DELETE, "/"+RESOURCE_1, null);
        assertEquals(204, httpResponse.getStatus());

        // Check that it is not in suspend anymore
        httpResponse = executeRequest(Method.GET, "/"+RESOURCE_1, null);
        assertEquals(404, httpResponse.getStatus());
    }

    @Test
    void list_applications_returns_the_correct_list_of_suspended_applications() {
        // Test that initially we have the empty set
        HttpResponse httpResponse = executeRequest(Method.GET, "", null);
        assertEquals(200, httpResponse.getStatus());
        Set<String> set = parseResponseContent(httpResponse, new TypeReference<>() {});
        assertEquals(0, set.size());

        // Add a couple of applications to maintenance
        executeRequest(Method.POST, "", RESOURCE_1);
        executeRequest(Method.POST, "", RESOURCE_2);

        // Test that we get them back
        httpResponse = executeRequest(Method.GET, "", null);
        assertEquals(200, httpResponse.getStatus());
        set = parseResponseContent(httpResponse, new TypeReference<>() {});
        assertEquals(2, set.size());

        // Remove suspend for the first resource
        executeRequest(Method.DELETE, "/"+RESOURCE_1, null);

        // Test that we are back to the start with the empty set
        httpResponse = executeRequest(Method.GET, "", null);
        assertEquals(200, httpResponse.getStatus());
        set = parseResponseContent(httpResponse, new TypeReference<>() {});
        assertEquals(1, set.size());
        assertEquals(RESOURCE_2, set.iterator().next());
    }

    private HttpResponse executeRequest(Method method, String relativePath, String applicationId) {
        String fullPath = "/orchestrator/v1/suspensions/applications" + relativePath;
        var builder = HttpRequestBuilder.create(method, fullPath);
        if (applicationId != null) {
            builder.withRequestContent(new ByteArrayInputStream(applicationId.getBytes(StandardCharsets.UTF_8)));
        }
        return testDriver.executeRequest(builder.build());
    }

    private <T> T parseResponseContent(HttpResponse response, TypeReference<T> type) {
        assertEquals(200, response.getStatus());
        return testDriver.parseJacksonResponseContent(response, type);
    }
}
