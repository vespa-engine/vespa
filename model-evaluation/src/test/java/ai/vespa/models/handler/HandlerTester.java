// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.handler;

import ai.vespa.models.evaluation.ModelsEvaluator;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.JsonFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;

class HandlerTester {

    private final ModelsEvaluationHandler handler;

    HandlerTester(ModelsEvaluator models) {
        this.handler = new ModelsEvaluationHandler(models, Executors.newSingleThreadExecutor());
    }

    void assertResponse(String url, int expectedCode) {
        assertResponse(url, Collections.emptyMap(), expectedCode, (String)null);
    }

    void assertResponse(String url, int expectedCode, String expectedResult) {
        assertResponse(url, Collections.emptyMap(), expectedCode, expectedResult);
    }

    void assertResponse(String url, int expectedCode, String expectedResult, Map<String, String> headers) {
        assertResponse(url, Collections.emptyMap(), expectedCode, expectedResult, headers);
    }

    void assertResponse(String url, Map<String, String> properties, int expectedCode, String expectedResult) {
        assertResponse(url, properties, expectedCode, expectedResult, Collections.emptyMap());
    }

    void assertResponse(String url, Map<String, String> properties, int expectedCode, String expectedResult, Map<String, String> headers) {
        HttpRequest getRequest = HttpRequest.createTestRequest(url, com.yahoo.jdisc.http.HttpRequest.Method.GET, null, properties);
        HttpRequest postRequest = HttpRequest.createTestRequest(url, com.yahoo.jdisc.http.HttpRequest.Method.POST, null, properties);
        if (headers.size() > 0) {
            headers.forEach((k,v) -> getRequest.getJDiscRequest().headers().add(k, v));
            headers.forEach((k,v) -> postRequest.getJDiscRequest().headers().add(k, v));
        }
        assertResponse(getRequest, expectedCode, expectedResult);
        assertResponse(postRequest, expectedCode, expectedResult);
    }

    void assertResponse(String url, Map<String, String> properties, int expectedCode, Tensor expectedResult) {
        HttpRequest getRequest = HttpRequest.createTestRequest(url, com.yahoo.jdisc.http.HttpRequest.Method.GET, null, properties);
        assertResponse(getRequest, expectedCode, expectedResult);
    }

    void assertResponse(HttpRequest request, int expectedCode, String expectedResult) {
        HttpResponse response = handler.handle(request);
        assertEquals("application/json", response.getContentType());
        assertEquals(expectedCode, response.getStatus());
        if (expectedResult != null) {
            assertEquals(expectedResult, getContents(response));
        }
    }

    void assertResponse(HttpRequest request, int expectedCode, Tensor expectedResult) {
        HttpResponse response = handler.handle(request);
        assertEquals("application/json", response.getContentType());
        assertEquals(expectedCode, response.getStatus());
        if (expectedResult != null) {
            String contents = getContents(response);
            Tensor result = JsonFormat.decode(expectedResult.type(), contents.getBytes(StandardCharsets.UTF_8));
            assertEquals(expectedResult, result);
        }
    }

    private String getContents(HttpResponse response) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            response.render(stream);
            return stream.toString();
        } catch (IOException e) {
            throw new Error(e);
        }
    }

}
