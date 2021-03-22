package com.yahoo.restapi;// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.test.json.JsonTestHelper;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.yahoo.jdisc.http.HttpRequest.Method;
import static com.yahoo.restapi.RestApi.route;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
class RestApiImplTest {

    @Test
    void routes_requests_to_correct_handler() {
        RestApi restApi = RestApi.builder()
                .addRoute(route("/api1/{*}").get(ctx -> new MessageResponse("get-method-response")))
                .addRoute(route("/api2/{*}").post(ctx -> new MessageResponse("post-method-response")))
                .setDefaultRoute(route("{*}").defaultHandler(ctx -> ErrorResponse.notFoundError("default-method-response")))
                .build();
        verifyJsonResponse(restApi, Method.GET, "/api1/subpath", null, 200, "{\"message\":\"get-method-response\"}");
        verifyJsonResponse(restApi, Method.POST, "/api1/subpath", "{}", 405, null);
        verifyJsonResponse(restApi, Method.GET, "/api2/subpath", null, 405, null);
        verifyJsonResponse(restApi, Method.POST, "/api2/subpath", "{}", 200, "{\"message\":\"post-method-response\"}");
        verifyJsonResponse(restApi, Method.PUT, "/api2/subpath", "{}", 405, null);
        verifyJsonResponse(restApi, Method.GET, "/unknown/subpath", null, 404, "{\"error-code\":\"NOT_FOUND\",\"message\":\"default-method-response\"}");
        verifyJsonResponse(restApi, Method.DELETE, "/unknown/subpath", "{}", 404, "{\"error-code\":\"NOT_FOUND\",\"message\":\"default-method-response\"}");
    }

    @Test
    void executes_filters_and_handler_in_correct_order() {
        List<String> actualEvaluationOrdering = new ArrayList<>();
        RestApi.MethodHandler<HttpResponse> handler = context -> {
            actualEvaluationOrdering.add("handler");
            return new MessageResponse("get-method-response");
        };
        class NamedTestFilter implements RestApi.Filter {
            final String name;
            NamedTestFilter(String name) { this.name = name; }

            @Override
            public HttpResponse filterRequest(RestApi.FilterContext context) {
                actualEvaluationOrdering.add("pre-" + name);
                HttpResponse response = context.executeNext();
                actualEvaluationOrdering.add("post-" + name);
                return response;
            }
        }
        RestApi restApi = RestApi.builder()
                .setDefaultRoute(route("{*}")
                        .defaultHandler(handler)
                        .addFilter(new NamedTestFilter("route-filter-1"))
                        .addFilter(new NamedTestFilter("route-filter-2")))
                .addFilter(new NamedTestFilter("global-filter-1"))
                .addFilter(new NamedTestFilter("global-filter-2"))
                .build();
        verifyJsonResponse(restApi, Method.GET, "/", null, 200, "{\"message\":\"get-method-response\"}");
        List<String> expectedOrdering = List.of(
                "pre-global-filter-1", "pre-global-filter-2", "pre-route-filter-1", "pre-route-filter-2",
                "handler",
                "post-route-filter-2", "post-route-filter-1", "post-global-filter-2", "post-global-filter-1");
        assertEquals(expectedOrdering, actualEvaluationOrdering);
    }

    @SuppressWarnings("divzero")
    @Test
    void handles_custom_response_and_exception_mapper() {
        RestApi restApi = RestApi.builder()
                .disableDefaultExceptionMappers()
                .disableDefaultResponseMappers()
                .addRoute(route("/long").get(ctx -> 123456L))
                .addRoute(route("/exception").get(ctx -> 123L / 0L))
                .addResponseMapper(Long.class, (entity, ctx) -> new MessageResponse("long value is " + entity))
                .addExceptionMapper(ArithmeticException.class, (exception, ctx) -> ErrorResponse.internalServerError("oops division by zero"))
                .build();
        verifyJsonResponse(restApi, Method.GET, "/long", null, 200, "{\"message\":\"long value is 123456\"}");
        verifyJsonResponse(restApi, Method.GET, "/exception", null, 500, "{\"message\":\"oops division by zero\", \"error-code\":\"INTERNAL_SERVER_ERROR\"}");
    }

    @Test
    void method_handler_can_consume_and_produce_json() {
        RestApi restApi = RestApi.builder()
                .addRoute(route("/api").post(
                        ctx -> ctx.requestContent().get().consumeJacksonEntity(TestEntity.class)))
                .build();
        String rawJson = "{\"mystring\":\"my-string-value\", \"myinstant\":\"2000-01-01T00:00:00Z\"}";
        verifyJsonResponse(restApi, Method.POST, "/api", rawJson, 200, rawJson);
    }

    private static void verifyJsonResponse(RestApi restApi, Method method, String path, String requestContent, int expectedStatusCode, String expectedJson) {
        HttpRequest testRequest = requestContent != null ?
                HttpRequest.createTestRequest(
                        path, method,
                        new ByteArrayInputStream(requestContent.getBytes(StandardCharsets.UTF_8)),
                        Map.of("Content-Type", "application/json")) :
                HttpRequest.createTestRequest(path, method);
        HttpResponse response = restApi.handleRequest(testRequest);
        assertEquals(expectedStatusCode, response.getStatus());
        if (expectedJson != null) {
            assertEquals("application/json", response.getContentType());
            var outputStream = new ByteArrayOutputStream();
            Exceptions.uncheck(() -> response.render(outputStream));
            String content = outputStream.toString(StandardCharsets.UTF_8);
            JsonTestHelper.assertJsonEquals(content, expectedJson);
        }
    }

    public static class TestEntity implements RestApi.JacksonRequestEntity, RestApi.JacksonResponseEntity {
        @JsonProperty("mystring") public String stringValue;
        @JsonProperty("myinstant") public Instant instantValue;
    }
}