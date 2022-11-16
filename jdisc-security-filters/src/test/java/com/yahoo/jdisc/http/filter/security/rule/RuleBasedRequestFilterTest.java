// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.container.jdisc.RequestHandlerTestDriver.MockResponseHandler;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.util.FilterTestUtils;
import com.yahoo.vespa.config.jdisc.http.filter.RuleBasedFilterConfig;
import com.yahoo.vespa.config.jdisc.http.filter.RuleBasedFilterConfig.DefaultRule;
import com.yahoo.vespa.config.jdisc.http.filter.RuleBasedFilterConfig.Rule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author bjorncs
 */
class RuleBasedRequestFilterTest {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    @Test
    void matches_rule_that_allows_all_methods_and_paths() {
        RuleBasedFilterConfig config = new RuleBasedFilterConfig.Builder()
                .dryrun(false)
                .defaultRule(new DefaultRule.Builder()
                        .action(DefaultRule.Action.Enum.BLOCK))
                .rule(new Rule.Builder()
                        .name("first")
                        .hostNames("myserver")
                        .pathExpressions(List.of())
                        .methods(List.of())
                        .action(Rule.Action.Enum.ALLOW))
                .build();

        Metric metric = mock(Metric.class);
        RuleBasedRequestFilter filter = new RuleBasedRequestFilter(metric, config);
        MockResponseHandler responseHandler = new MockResponseHandler();
        filter.filter(request("PATCH", "http://myserver:80/path-to-resource%2F"), responseHandler);

        assertAllowed(responseHandler, metric);

    }

    @Test
    void performs_action_on_first_matching_rule() throws IOException {
        RuleBasedFilterConfig config = new RuleBasedFilterConfig.Builder()
                .dryrun(false)
                .defaultRule(new DefaultRule.Builder()
                        .action(DefaultRule.Action.Enum.ALLOW))
                .rule(new Rule.Builder()
                        .name("first")
                        .pathExpressions("/path-to-resource")
                        .methods(Rule.Methods.Enum.DELETE)
                        .action(Rule.Action.Enum.BLOCK)
                        .blockResponseCode(403))
                .rule(new Rule.Builder()
                        .name("second")
                        .pathExpressions("/path-to-resource")
                        .methods(Rule.Methods.Enum.GET)
                        .action(Rule.Action.Enum.BLOCK)
                        .blockResponseCode(404))
                .build();

        Metric metric = mock(Metric.class);
        RuleBasedRequestFilter filter = new RuleBasedRequestFilter(metric, config);
        MockResponseHandler responseHandler = new MockResponseHandler();
        filter.filter(request("GET", "http://myserver:80/path-to-resource"), responseHandler);

        assertBlocked(responseHandler, metric, 404, "");
    }

    @Test
    void performs_default_action_if_no_rule_matches() throws IOException {
        RuleBasedFilterConfig config = new RuleBasedFilterConfig.Builder()
                .dryrun(false)
                .defaultRule(new DefaultRule.Builder()
                        .action(DefaultRule.Action.Enum.BLOCK)
                        .blockResponseCode(403)
                        .blockResponseMessage("my custom message"))
                .rule(new Rule.Builder()
                        .name("rule")
                        .pathExpressions("/path-to-resource")
                        .methods(Rule.Methods.Enum.GET)
                        .action(Rule.Action.Enum.ALLOW))
                .build();

        Metric metric = mock(Metric.class);
        RuleBasedRequestFilter filter = new RuleBasedRequestFilter(metric, config);
        MockResponseHandler responseHandler = new MockResponseHandler();
        filter.filter(request("POST", "http://myserver:80/"), responseHandler);

        assertBlocked(responseHandler, metric, 403, "my custom message");
    }

    @Test
    void matches_rule_with_multiple_alternatives_for_host_path_and_method() throws IOException {
        RuleBasedFilterConfig config = new RuleBasedFilterConfig.Builder()
                .dryrun(false)
                .defaultRule(new DefaultRule.Builder()
                        .action(DefaultRule.Action.Enum.ALLOW))
                .rule(new Rule.Builder()
                        .name("rule")
                        .hostNames(Set.of("server1", "server2", "server3"))
                        .pathExpressions(Set.of("/path-to-resource/{*}", "/another-path"))
                        .methods(Set.of(Rule.Methods.Enum.GET, Rule.Methods.POST, Rule.Methods.DELETE))
                        .action(Rule.Action.Enum.BLOCK)
                        .blockResponseCode(404)
                        .blockResponseMessage("not found"))
                .build();

        Metric metric = mock(Metric.class);
        RuleBasedRequestFilter filter = new RuleBasedRequestFilter(metric, config);
        MockResponseHandler responseHandler = new MockResponseHandler();
        filter.filter(request("POST", "https://server1:443/path-to-resource/id/1/subid/2"), responseHandler);

        assertBlocked(responseHandler, metric, 404, "not found");
    }

    @Test
    void no_filtering_if_request_is_allowed() {
        RuleBasedFilterConfig config = new RuleBasedFilterConfig.Builder()
                .dryrun(false)
                .defaultRule(new DefaultRule.Builder()
                        .action(DefaultRule.Action.Enum.ALLOW))
                .build();

        Metric metric = mock(Metric.class);
        RuleBasedRequestFilter filter = new RuleBasedRequestFilter(metric, config);
        MockResponseHandler responseHandler = new MockResponseHandler();
        filter.filter(request("DELETE", "http://myserver:80/"), responseHandler);

        assertAllowed(responseHandler, metric);
    }

    @Test
    void includes_default_rule_response_headers_in_response_for_blocked_request() throws IOException {
        RuleBasedFilterConfig config = new RuleBasedFilterConfig.Builder()
                .dryrun(false)
                .defaultRule(new DefaultRule.Builder()
                        .action(DefaultRule.Action.Enum.BLOCK)
                        .blockResponseHeaders(new DefaultRule.BlockResponseHeaders.Builder()
                                .name("Response-Header-1").value("first-header"))
                        .blockResponseHeaders(new DefaultRule.BlockResponseHeaders.Builder()
                                .name("Response-Header-2").value("second-header")))
                .build();

        Metric metric = mock(Metric.class);
        RuleBasedRequestFilter filter = new RuleBasedRequestFilter(metric, config);
        MockResponseHandler responseHandler = new MockResponseHandler();
        filter.filter(request("GET", "http://myserver:80/"), responseHandler);

        assertBlocked(responseHandler, metric, 403, "");
        Response response = responseHandler.getResponse();
        assertResponseHeader(response, "Response-Header-1", "first-header");
        assertResponseHeader(response, "Response-Header-2", "second-header");
    }

    @Test
    void includes_rule_response_headers_in_response_for_blocked_request() throws IOException {
        RuleBasedFilterConfig config = new RuleBasedFilterConfig.Builder()
                .dryrun(false)
                .defaultRule(new DefaultRule.Builder()
                        .action(DefaultRule.Action.Enum.ALLOW))
                .rule(new Rule.Builder()
                        .name("rule")
                        .pathExpressions("/path-to-resource")
                        .action(Rule.Action.Enum.BLOCK)
                        .blockResponseHeaders(new Rule.BlockResponseHeaders.Builder()
                                .name("Response-Header-1").value("first-header")))
                .build();

        Metric metric = mock(Metric.class);
        RuleBasedRequestFilter filter = new RuleBasedRequestFilter(metric, config);
        MockResponseHandler responseHandler = new MockResponseHandler();
        filter.filter(request("GET", "http://myserver/path-to-resource"), responseHandler);

        assertBlocked(responseHandler, metric, 403, "");
        Response response = responseHandler.getResponse();
        assertResponseHeader(response, "Response-Header-1", "first-header");
    }

    @Test
    void dryrun_does_not_block() {
        RuleBasedFilterConfig config = new RuleBasedFilterConfig.Builder()
                .dryrun(true)
                .defaultRule(new DefaultRule.Builder()
                        .action(DefaultRule.Action.Enum.BLOCK))
                .build();

        Metric metric = mock(Metric.class);
        RuleBasedRequestFilter filter = new RuleBasedRequestFilter(metric, config);
        MockResponseHandler responseHandler = new MockResponseHandler();
        filter.filter(request("GET", "http://myserver/"), responseHandler);
        assertNull(responseHandler.getResponse());
    }

    private void assertResponseHeader(Response response, String name, String expectedValue) {
        List<String> actualValues = response.headers().get(name);
        assertNotNull(actualValues);
        assertEquals(1, actualValues.size());
        assertEquals(expectedValue, actualValues.get(0));
    }

    private static DiscFilterRequest request(String method, String uri) {
        return FilterTestUtils.newRequestBuilder().withMethod(method).withUri(uri).build();
    }

    private static void assertAllowed(MockResponseHandler handler, Metric metric) {
        verify(metric).add(eq("jdisc.http.filter.rule.allowed_requests"), eq(1L), any());
        assertNull(handler.getResponse());
    }

    private static void assertBlocked(MockResponseHandler handler, Metric metric, int expectedCode, String expectedMessage) throws IOException {
        verify(metric).add(eq("jdisc.http.filter.rule.blocked_requests"), eq(1L), any());
        Response response = handler.getResponse();
        assertNotNull(response);
        assertEquals(expectedCode, response.getStatus());
        ObjectNode expectedJson = jsonMapper.createObjectNode();
        expectedJson.put("message", expectedMessage).put("code", expectedCode);
        JsonNode actualJson = jsonMapper.readTree(handler.readAll().getBytes());
        assertEquals(expectedJson, actualJson);
    }

}