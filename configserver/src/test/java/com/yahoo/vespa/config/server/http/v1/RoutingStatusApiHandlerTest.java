// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v1;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequestBuilder;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.path.Path;
import com.yahoo.restapi.RestApiTestDriver;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 * @author mpolden
 */
public class RoutingStatusApiHandlerTest {

    private static final ApplicationId instance = ApplicationId.from("t1", "a1", "i1");
    private static final String upstreamName = "test-upstream-name";

    private final Curator curator = new MockCurator();
    private final ManualClock clock = new ManualClock();

    private RestApiTestDriver testDriver;

    @Before
    public void before() {
        RoutingStatusApiHandler requestHandler = new RoutingStatusApiHandler(RestApiTestDriver.createHandlerTestContext(),
                                                                             curator,
                                                                             clock);
        testDriver = RestApiTestDriver.newBuilder(requestHandler).build();
    }

    @Test
    public void list_deployment_status() {
        List<String> expected = List.of("foo", "bar");
        for (String upstreamName : expected) {
            executeRequest(Method.PUT, "/routing/v1/status/" + upstreamName + "?application=" + instance.serializedForm(),
                           statusOut());
        }
        String actual = responseAsString(executeRequest(Method.GET, "/routing/v1/status", null));
        assertEquals("[\"bar\",\"foo\"]", actual);
    }

    @Test
    public void get_deployment_status() {
        String response = responseAsString(executeRequest(Method.GET, "/routing/v1/status/" + upstreamName + "?application=" + instance.serializedForm(), null));
        assertEquals(response("IN", "", "", clock.instant()), response);
    }

    @Test
    public void get_deployment_status_v2() {
        String response = responseAsString(executeRequest(Method.GET, "/routing/v2/status/", null));
        assertEquals("{\"inactiveDeployments\":[],\"zoneActive\":true}", response);

        // Set deployment out
        executeRequest(Method.PUT, "/routing/v1/status/" + upstreamName + "?application=" + instance.serializedForm(), statusOut());
        response = responseAsString(executeRequest(Method.GET, "/routing/v2/status/", null));
        assertEquals("{\"inactiveDeployments\":[{\"upstreamName\":\"test-upstream-name\"}],\"zoneActive\":true}", response);

        // Set zone out
        executeRequest(Method.PUT, "/routing/v1/status/zone", null);
        response = responseAsString(executeRequest(Method.GET, "/routing/v2/status/", null));
        assertEquals("{\"inactiveDeployments\":[{\"upstreamName\":\"test-upstream-name\"}],\"zoneActive\":false}", response);
    }

    @Test
    public void set_deployment_status() {
        String response = responseAsString(executeRequest(Method.PUT, "/routing/v1/status/" + upstreamName + "?application=" + instance.serializedForm(),
                                                          statusOut()));
        assertEquals(response("OUT", "issue-XXX", "operator", clock.instant()), response);

        // Read status stored in old format (path exists, but without content)
        curator.set(Path.fromString("/routing/v1/status/" + upstreamName), new byte[0]);
        response = responseAsString(executeRequest(Method.GET, "/routing/v1/status/" + upstreamName + "?application=" + instance.serializedForm(), null));
        assertEquals(response("OUT", "", "", clock.instant()), response);

        // Change status of multiple upstreams
        String upstreamName2 = "upstream2";
        String upstreams = upstreamName + "," + upstreamName2 + "," + upstreamName2;
        response = responseAsString(executeRequest(Method.PUT, "/routing/v1/status/" + upstreams + "?application=" + instance.serializedForm(),
                                                   statusOut()));
        String outResponse = response("OUT", "issue-XXX", "operator", clock.instant());
        assertEquals(outResponse, response);
        for (var upstreamName : List.of(upstreamName, upstreamName2)) {
            response = responseAsString(executeRequest(Method.GET, "/routing/v1/status/" + upstreamName + "?application=" + instance.serializedForm(), null));
            assertEquals(outResponse, response);
        }
    }

    @Test
    public void fail_on_invalid_upstream_name() {
        HttpResponse response = executeRequest(Method.GET, "/routing/v1/status/" + upstreamName + "%20invalid", null);
        assertEquals(400, response.getStatus());
    }

    @Test
    public void fail_on_changing_routing_status_without_request_content() {
        HttpResponse response = executeRequest(Method.PUT, "/routing/v1/status/" + upstreamName + "?application=" + instance.serializedForm(), null);
        assertEquals(400, response.getStatus());
    }

    @Test
    public void zone_status_out_overrides_deployment_status() {
        // Setting zone out overrides deployment status
        executeRequest(Method.PUT, "/routing/v1/status/zone", null);
        String response = responseAsString(executeRequest(Method.GET, "/routing/v1/status/" + upstreamName + "?application=" + instance.serializedForm(), null));
        assertEquals(response("OUT", "Rotation is OUT because the zone is OUT (actual deployment status is IN)", "operator", clock.instant()), response);

        // Setting zone back in falls back to deployment status, which is also out
        executeRequest(Method.DELETE, "/routing/v1/status/zone", null);
        String response2 = responseAsString(executeRequest(Method.PUT, "/routing/v1/status/" + upstreamName + "?application=" + instance.serializedForm(),
                                                           statusOut()));
        assertEquals(response("OUT", "issue-XXX", "operator", clock.instant()), response2);

        // Deployment status is changed to in
        String response3 = responseAsString(executeRequest(Method.PUT, "/routing/v1/status/" + upstreamName + "?application=" + instance.serializedForm(),
                                                           requestContent("IN", "all good")));
        assertEquals(response("IN", "all good", "operator", clock.instant()), response3);
    }

    @Test
    public void set_zone_status() {
        executeRequest(Method.PUT, "/routing/v1/status/zone", null);
        String response = responseAsString(executeRequest(Method.GET, "/routing/v1/status/zone", null));
        assertEquals("{\"status\":\"OUT\"}", response);
        executeRequest(Method.DELETE, "/routing/v1/status/zone", null);
        response = responseAsString(executeRequest(Method.GET, "/routing/v1/status/zone", null));
        assertEquals("{\"status\":\"IN\"}", response);
    }

    private HttpResponse executeRequest(Method method, String path, String requestContent) {
        var builder = HttpRequestBuilder.create(method, path);
        if (requestContent != null) {
            builder.withRequestContent(new ByteArrayInputStream(requestContent.getBytes(StandardCharsets.UTF_8)));
        }
        return testDriver.executeRequest(builder.build());
    }

    private static String responseAsString(HttpResponse response) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        uncheck(() -> response.render(out));
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String statusOut() {
        return requestContent("OUT", "issue-XXX");
    }

    private static String requestContent(String status, String cause) {
        return "{\"status\": \"" + status + "\", \"agent\":\"operator\", \"cause\": \"" + cause + "\"}";
    }

    private static String response(String status, String reason, String agent, Instant instant) {
        return "{\"status\":\"" + status + "\",\"cause\":\"" + reason + "\",\"agent\":\"" + agent + "\",\"lastUpdate\":" + instant.getEpochSecond() + "}";
    }

}
