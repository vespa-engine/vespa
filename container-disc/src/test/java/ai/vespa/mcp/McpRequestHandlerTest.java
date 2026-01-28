// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.mcp;

import ai.vespa.mcp.api.McpSpecProvider;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.HttpRequestBuilder;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.restapi.RestApiTestDriver;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link McpRequestHandler}.
 *
 * @author Edvard Dingsør
 */
class McpRequestHandlerTest {

    private RestApiTestDriver createTestDriver() {
        var registry = new ComponentRegistry<McpSpecProvider>();
        return RestApiTestDriver.newBuilder(context -> new McpRequestHandler(context, registry)).build();
    }

    @Test
    void testInvalidPathReturns404() {
        var testDriver = createTestDriver();
        var request = HttpRequestBuilder.create(Method.POST, "/invalid/path").build();
        var response = testDriver.executeRequest(request);
        assertEquals(404, response.getStatus());
    }

    @Test
    void testUnsupportedMethodReturns405() {
        var testDriver = createTestDriver();
        var request = HttpRequestBuilder.create(Method.PUT, "/mcp/").build();
        var response = testDriver.executeRequest(request);
        assertEquals(405, response.getStatus());
    }

    @Test
    void testGetReturns405() {
        var testDriver = createTestDriver();
        var request = HttpRequestBuilder.create(Method.GET, "/mcp/").build();
        var response = testDriver.executeRequest(request);
        assertEquals(405, response.getStatus());
    }

    @Test
    void testDeleteReturns405() {
        var testDriver = createTestDriver();
        var request = HttpRequestBuilder.create(Method.DELETE, "/mcp/").build();
        var response = testDriver.executeRequest(request);
        assertEquals(405, response.getStatus());
    }

    @Test
    void testPostReturnsValidJsonRpcResponse() throws IOException {
        var testDriver = createTestDriver();
        var requestBody = "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"params\":{},\"id\":1}";
        var request = HttpRequestBuilder.create(Method.POST, "/mcp/")
                .withRequestContent(new ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8)))
                .withHeader("Accept", "application/json")
                .build();

        var response = testDriver.executeRequest(request);
        assertEquals(200, response.getStatus());

        var body = renderResponse(response);
        assertEquals("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}", body);
    }

    @Test
    void testPostWithInvalidJsonReturns400() {
        var testDriver = createTestDriver();
        var requestBody = "not valid json";
        var request = HttpRequestBuilder.create(Method.POST, "/mcp/")
                .withRequestContent(new ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8)))
                .withHeader("Accept", "application/json")
                .build();

        var response = testDriver.executeRequest(request);
        assertEquals(400, response.getStatus());
    }

    private static String renderResponse(HttpResponse response) throws IOException {
        var out = new ByteArrayOutputStream();
        response.render(out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
