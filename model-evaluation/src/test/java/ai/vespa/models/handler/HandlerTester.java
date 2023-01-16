// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.handler;

import ai.vespa.models.evaluation.ModelsEvaluator;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.JsonFormat;
import com.yahoo.text.JSON;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static com.yahoo.slime.SlimeUtils.jsonToSlime;

class HandlerTester {

    private final ModelsEvaluationHandler handler;

    private static Predicate<String> nop() {
        return s -> true;
    }
    private static Predicate<String> matchString(String expected) {
        return s -> {
            //System.out.println("Expected: " + expected);
            //System.out.println("Actual:   " + s);
            return expected.equals(s);
        };
    }
    private static Predicate<String> matchJsonString(String expected) {
        return s -> {
            //System.out.println("Expected: " + expected);
            //System.out.println("Actual:   " + s);
            return JSON.canonical(expected).equals(JSON.canonical(s));
        };
    }
    public static Predicate<String> matchJson(String... expectedJson) {
        var jExp = String.join("\n", expectedJson).replaceAll("'", "\"");
        var expected = jsonToSlime(jExp);
        return s -> {
            var got = jsonToSlime(s);
            boolean result = got.equalTo(expected);
            if (!result) {
                System.err.println("got:");
                System.err.println(got);
                System.err.println("expected:");
                System.err.println(expected);
            }
            return result;
        };
    }

    HandlerTester(ModelsEvaluator models) {
        this.handler = new ModelsEvaluationHandler(models, Executors.newSingleThreadExecutor());
    }

    void assertResponse(String url, int expectedCode) {
        checkResponse(url, expectedCode, nop());
    }

    void assertResponse(String url, int expectedCode, String expectedResult) {
        assertResponse(url, Map.of(), expectedCode, expectedResult);
    }

    void checkResponse(String url, int expectedCode, Predicate<String> check) {
        checkResponse(url, Map.of(), expectedCode, check, Map.of());
    }

    void assertResponse(String url, int expectedCode, String expectedResult, Map<String, String> headers) {
        assertResponse(url, Map.of(), expectedCode, expectedResult, headers);
    }

    void assertResponse(String url, Map<String, String> properties, int expectedCode, String expectedResult) {
        assertResponse(url, properties, expectedCode, expectedResult, Map.of());
    }

    void assertResponse(String url, Map<String, String> properties, int expectedCode, String expectedResult, Map<String, String> headers) {
        checkResponse(url, properties, expectedCode, matchJsonString(expectedResult), headers);
    }

    void assertStringResponse(String url, Map<String, String> properties, int expectedCode, String expectedResult, Map<String, String> headers) {
        checkResponse(url, properties, expectedCode, matchString(expectedResult), headers);
    }

    void checkResponse(String url, Map<String, String> properties, int expectedCode, Predicate<String> check, Map<String, String> headers) {
        HttpRequest getRequest = HttpRequest.createTestRequest(url, com.yahoo.jdisc.http.HttpRequest.Method.GET, null, properties);
        HttpRequest postRequest = HttpRequest.createTestRequest(url, com.yahoo.jdisc.http.HttpRequest.Method.POST, null, properties);
        if (headers.size() > 0) {
            headers.forEach((k,v) -> getRequest.getJDiscRequest().headers().add(k, v));
            headers.forEach((k,v) -> postRequest.getJDiscRequest().headers().add(k, v));
        }
        checkResponse(getRequest, expectedCode, check);
        checkResponse(postRequest, expectedCode, check);
    }

    void assertResponse(String url, Map<String, String> properties, int expectedCode, Tensor expectedResult) {
        HttpRequest getRequest = HttpRequest.createTestRequest(url, com.yahoo.jdisc.http.HttpRequest.Method.GET, null, properties);
        assertResponse(getRequest, expectedCode, expectedResult);
    }

    void checkResponse(HttpRequest request, int expectedCode, Predicate<String> check) {
        HttpResponse response = handler.handle(request);
        assertEquals("application/json", response.getContentType());
        assertEquals(true, check.test(getContents(response)));
        assertEquals(expectedCode, response.getStatus());
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
