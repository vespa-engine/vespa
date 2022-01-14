// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.container.jdisc.HttpRequestBuilder;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.RestApiTestDriver;
import com.yahoo.test.json.JsonTestHelper;
import com.yahoo.vespa.orchestrator.BatchHostNameNotFoundException;
import com.yahoo.vespa.orchestrator.BatchInternalErrorException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.orchestrator.policy.BatchHostStateChangeDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static com.yahoo.jdisc.http.HttpRequest.Method;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 * @author bjorncs
 */
class HostSuspensionRequestHandlerTest {

    private final Clock clock = mock(Clock.class);

    @BeforeEach
    public void setUp() {
        when(clock.instant()).thenReturn(Instant.now());
    }

    @Test
    void returns_200_on_success_batch() throws IOException {
        RestApiTestDriver testDriver = createTestDriver(HostRequestHandlerTest.createAlwaysAllowOrchestrator(clock));
        HttpResponse response = executeSuspendAllRequest(testDriver, "parentHostname", List.of("hostname1", "hostname2"));
        assertSuccess(response);
    }

    @Test
    void returns_200_empty_batch() throws IOException {
        RestApiTestDriver testDriver = createTestDriver(HostRequestHandlerTest.createAlwaysAllowOrchestrator(clock));
        HttpResponse response = executeSuspendAllRequest(testDriver, "parentHostname", List.of());
        assertSuccess(response);
    }

    // Note: Missing host is 404 for a single-host, but 400 for multi-host (batch).
    // This is so because the hostname is part of the URL path for single-host, while the
    // hostnames are part of the request body for multi-host.
    @Test
    void returns_400_when_host_unknown_for_batch() {
        RestApiTestDriver testDriver = createTestDriver(HostRequestHandlerTest.createHostNotFoundOrchestrator(clock));
        HttpResponse response = executeSuspendAllRequest(testDriver, "parentHostname", List.of("hostname1", "hostname2"));
        assertEquals(400, response.getStatus());
    }

    @Test
    void returns_409_when_request_rejected_by_policies_for_batch() {
        OrchestratorImpl alwaysRejectResolver = HostRequestHandlerTest.createAlwaysRejectResolver(clock);
        RestApiTestDriver testDriver = createTestDriver(alwaysRejectResolver);
        HttpResponse response = executeSuspendAllRequest(testDriver, "parentHostname", List.of("hostname1", "hostname2"));
        assertEquals(409, response.getStatus());
    }


    @Test
    void throws_409_on_suspendAll_timeout() throws BatchHostStateChangeDeniedException, BatchHostNameNotFoundException, BatchInternalErrorException {
        Orchestrator orchestrator = mock(Orchestrator.class);
        doThrow(new UncheckedTimeoutException("Timeout Message")).when(orchestrator).suspendAll(any(), any());
        RestApiTestDriver testDriver = createTestDriver(orchestrator);
        HttpResponse response = executeSuspendAllRequest(testDriver, "parenthost", List.of("h1", "h2", "h3"));
        assertEquals(409, response.getStatus());
    }

    private static RestApiTestDriver createTestDriver(Orchestrator orchestrator) {
        return RestApiTestDriver.newBuilder(ctx -> new HostSuspensionRequestHandler(ctx, orchestrator))
                .build();
    }

    private static HttpResponse executeSuspendAllRequest(RestApiTestDriver testDriver, String parentHostname, List<String> hostnames) {
        var builder = HttpRequestBuilder.create(Method.PUT, "/orchestrator/v1/suspensions/hosts/" + parentHostname);
        hostnames.forEach(hostname -> builder.withQueryParameter("hostname", hostname));
        return testDriver.executeRequest(builder.build());
    }

    private static void assertSuccess(HttpResponse response) throws IOException {
        assertEquals(200, response.getStatus());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.render(out);
        JsonTestHelper.assertJsonEquals(out.toString(), "{\"failure-reason\": null}");
    }

}
