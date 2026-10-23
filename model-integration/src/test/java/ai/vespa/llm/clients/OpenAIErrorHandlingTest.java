// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModelException;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.StringPrompt;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import com.openai.errors.SseException;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.UnexpectedStatusCodeException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that failures from the OpenAI SDK are reported as {@link LanguageModelException} with a status code,
 * both from {@link OpenAI#complete} and through the future returned by {@link OpenAI#completeAsync},
 * using a fake OpenAI endpoint.
 *
 * @author glebashnik
 */
public class OpenAIErrorHandlingTest {

    private static final String API_KEY = "test-api-key";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final String ERROR_BODY = """
            {"error": {"message": "Incorrect API key provided", "type": "invalid_request_error", "code": "invalid_api_key"}}
            """;

    private MockWebServer server;
    private OpenAI openai;  // Keeps the client under test reachable, see openai(String)

    @BeforeEach
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void testSuccessfulCompletion() {
        server.enqueue(json(200, completion("Hello world", "length")));
        var completions = openai().complete(prompt(), parameters());
        assertEquals(1, completions.size());
        assertEquals("Hello world", completions.get(0).text());
        assertEquals(Completion.FinishReason.length, completions.get(0).finishReason());
    }

    @Test
    public void testSuccessfulStream() {
        server.enqueue(sse(chunk("Hello", null), chunk(" world", null), chunk(null, "stop"), "[DONE]"));
        var completions = new ArrayList<Completion>();
        var finishReason = openai().completeAsync(prompt(), parameters(), completions::add).join();
        assertEquals(Completion.FinishReason.stop, finishReason);
        assertEquals("Hello world", text(completions));
    }

    @Test
    public void testHttpErrorStatusIsReported() {
        server.enqueue(json(401, ERROR_BODY));
        var exception = assertSyncFails(401, UnauthorizedException.class);
        assertTrue(exception.getMessage().contains("Incorrect API key provided"), exception.getMessage());

        server.enqueue(json(429, ERROR_BODY));
        assertSyncFails(429, RateLimitException.class);

        server.enqueue(json(503, ERROR_BODY));
        assertSyncFails(503, InternalServerException.class);

        server.enqueue(json(418, ERROR_BODY));
        assertSyncFails(418, UnexpectedStatusCodeException.class);
    }

    @Test
    public void testHttpErrorStatusIsReportedAsync() {
        server.enqueue(json(401, ERROR_BODY));
        var exception = assertAsyncFails(401, UnauthorizedException.class);
        assertTrue(exception.getMessage().contains("Incorrect API key provided"), exception.getMessage());

        server.enqueue(json(429, ERROR_BODY));
        assertAsyncFails(429, RateLimitException.class);

        server.enqueue(json(500, ERROR_BODY));
        assertAsyncFails(500, InternalServerException.class);
    }

    @Test
    public void testErrorEventInStream() {
        server.enqueue(sse(chunk("Hello", null),
                           "{\"error\": {\"message\": \"The server had an error\", \"type\": \"server_error\"}}"));
        var completions = new ArrayList<Completion>();
        var future = openai().completeAsync(prompt(), parameters(), completions::add);
        var exception = assertFailsWith(future, 502, SseException.class);
        assertTrue(exception.getMessage().contains("The server had an error"), exception.getMessage());
        assertEquals("Hello", text(completions), "Content received before the error is delivered");
    }

    @Test
    public void testStreamEndingWithoutFinishReason() {
        server.enqueue(sse(chunk("Hello", null)));
        var completions = new ArrayList<Completion>();
        var future = openai().completeAsync(prompt(), parameters(), completions::add);
        var exception = assertFailsWith(future, 502, null);
        assertTrue(exception.getMessage().contains("ended before a finish reason"), exception.getMessage());
        assertEquals("Hello", text(completions));
    }

    @Test
    public void testFinishReasonErrorInStream() {
        // The future fails since callers may ignore the finish reason of the completions
        server.enqueue(sse(chunk("Hel", null), chunk("lo", "error"), "[DONE]"));
        var completions = new ArrayList<Completion>();
        var future = openai().completeAsync(prompt(), parameters(), completions::add);
        assertFailsWith(future, 502, null);
        assertEquals(Completion.FinishReason.none, completions.get(0).finishReason());
        assertEquals(Completion.FinishReason.error, completions.get(1).finishReason());

        // Also when the error chunk has no content, which is the usual shape of such a chunk
        server.enqueue(sse(chunk("Hello", null), chunk(null, "error"), "[DONE]"));
        completions.clear();
        assertFailsWith(openai().completeAsync(prompt(), parameters(), completions::add), 502, null);
        assertEquals("Hello", text(completions));
    }

    @Test
    public void testConnectionCutDuringStream() {
        server.enqueue(sse(chunk("Hello", null), chunk(" world", null), chunk(null, "stop"), "[DONE]")
                               .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
        assertAsyncFails(503, OpenAIIoException.class);
    }

    @Test
    public void testFinishReasonErrorInCompletion() {
        // Callers of complete() only look at the text, so an error must be reported as an exception
        server.enqueue(json(200, completion("Hel", "error")));
        assertSyncFails(502, null);
    }

    @Test
    public void testMalformedResponse() {
        server.enqueue(json(200, "this is not json"));
        assertSyncFails(502, OpenAIInvalidDataException.class);

        server.enqueue(json(200, "{\"unexpected\": true}"));
        assertSyncFails(502, OpenAIInvalidDataException.class);

        server.enqueue(sse("this is not json"));
        assertAsyncFails(502, OpenAIInvalidDataException.class);
    }

    @Test
    public void testUnreachableEndpoint() throws IOException {
        var openai = openai();  // Captures the endpoint before the server is shut down
        server.shutdown();
        assertFailsWith(() -> openai.complete(prompt(), parameters()), 503, OpenAIIoException.class);
        assertFailsWith(openai.completeAsync(prompt(), parameters(), completion -> {}), 503, OpenAIIoException.class);
    }

    @Test
    public void testTimeout() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        assertSyncFails(504, OpenAIIoException.class);

        assertEquals(504, OpenAI.toLanguageModelException(new OpenAIIoException("timeout", new SocketTimeoutException())).code());
        assertEquals(503, OpenAI.toLanguageModelException(new OpenAIIoException("refused", new ConnectException())).code());
    }

    @Test
    public void testInvalidEndpoint() {
        var openai = openai("not a url");
        assertFailsWith(() -> openai.complete(prompt(), parameters()), 400, IllegalArgumentException.class);
        assertFailsWith(openai.completeAsync(prompt(), parameters(), completion -> {}), 400, IllegalArgumentException.class);
    }

    @Test
    public void testInvalidJsonSchemaIsReportedThroughFuture() {
        var parameters = new InferenceParameters(Map.of(InferenceParameters.OPTION_JSON_SCHEMA, "{not a schema")::get);
        assertFailsWith(openai().completeAsync(prompt(), parameters, completion -> {}), 400, null);
    }

    @Test
    public void testConsumerExceptionIsPassedThrough() {
        server.enqueue(sse(chunk("Hello", null), chunk(null, "stop"), "[DONE]"));
        var consumerException = new IllegalArgumentException("Consumer failed");
        var future = openai().completeAsync(prompt(), parameters(), completion -> { throw consumerException; });
        var exception = assertThrows(CompletionException.class, future::join);
        assertSame(consumerException, exception.getCause());
    }

    private LanguageModelException assertSyncFails(int expectedCode, Class<? extends Throwable> expectedCause) {
        return assertFailsWith(() -> openai().complete(prompt(), parameters()), expectedCode, expectedCause);
    }

    private LanguageModelException assertAsyncFails(int expectedCode, Class<? extends Throwable> expectedCause) {
        return assertFailsWith(openai().completeAsync(prompt(), parameters(), completion -> {}), expectedCode, expectedCause);
    }

    private static LanguageModelException assertFailsWith(Runnable call, int expectedCode,
                                                          Class<? extends Throwable> expectedCause) {
        var exception = assertThrows(LanguageModelException.class, call::run);
        return assertCodeAndCause(exception, expectedCode, expectedCause);
    }

    private static LanguageModelException assertFailsWith(CompletableFuture<?> future, int expectedCode,
                                                          Class<? extends Throwable> expectedCause) {
        var exception = assertThrows(CompletionException.class, future::join);
        var cause = assertInstanceOf(LanguageModelException.class, exception.getCause(),
                                     "Future should fail with LanguageModelException, failed with " + exception.getCause());
        return assertCodeAndCause(cause, expectedCode, expectedCause);
    }

    private static LanguageModelException assertCodeAndCause(LanguageModelException exception, int expectedCode,
                                                             Class<? extends Throwable> expectedCause) {
        assertEquals(expectedCode, exception.code(), describe(exception));
        if (expectedCause != null)
            assertInstanceOf(expectedCause, exception.getCause(), describe(exception));
        return exception;
    }

    private static String describe(Throwable exception) {
        var description = new StringBuilder();
        for (Throwable t = exception; t != null; t = t.getCause())
            description.append(description.isEmpty() ? "" : " <- ")
                       .append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
        return description.toString();
    }

    private OpenAI openai() {
        return openai(server.url("/v1/").toString());
    }

    /**
     * Creates a client without the SDK's retries and with a short timeout, to keep the tests fast.
     * The SDK closes a client, and fails its in-flight requests, when the client is garbage collected,
     * so the client is kept reachable from this test, like the component keeps its clients in fields.
     */
    private OpenAI openai(String endpoint) {
        var config = new LlmClientConfig.Builder().apiKeySecretName("openai").endpoint(endpoint).build();
        openai = new OpenAI(config, new OpenAITest.MockSecrets(API_KEY)) {
            @Override
            OpenAIClient getSyncClient(String apiKey, String endpoint) {
                defaultSyncClient = OpenAIOkHttpClient.builder().apiKey(apiKey).baseUrl(endpoint).responseValidation(false)
                        .maxRetries(0).timeout(TIMEOUT).build();
                return defaultSyncClient;
            }
            @Override
            OpenAIClientAsync getAsyncClient(String apiKey, String endpoint) {
                defaultAsyncClient = OpenAIOkHttpClientAsync.builder().apiKey(apiKey).baseUrl(endpoint).responseValidation(false)
                        .maxRetries(0).timeout(TIMEOUT).build();
                return defaultAsyncClient;
            }
        };
        return openai;
    }

    private static StringPrompt prompt() {
        return StringPrompt.from("hello");
    }

    private static InferenceParameters parameters() {
        return new InferenceParameters(key -> null);
    }

    private static String text(List<Completion> completions) {
        return String.join("", completions.stream().map(Completion::text).toList());
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body);
    }

    private static MockResponse sse(String... events) {
        var body = new StringBuilder();
        for (var event : events) body.append("data: ").append(event).append("\n\n");
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(body.toString());
    }

    private static String completion(String content, String finishReason) {
        return "{\"id\": \"chatcmpl-1\", \"object\": \"chat.completion\", \"created\": 1, \"model\": \"gpt-4o-mini\", " +
               "\"choices\": [{\"index\": 0, \"message\": {\"role\": \"assistant\", \"content\": \"" + content + "\"}, " +
               "\"finish_reason\": \"" + finishReason + "\"}]}";
    }

    private static String chunk(String content, String finishReason) {
        var delta = content == null ? "{}" : "{\"content\": \"" + content + "\"}";
        var finish = finishReason == null ? "null" : "\"" + finishReason + "\"";
        return "{\"id\": \"chatcmpl-1\", \"object\": \"chat.completion.chunk\", \"created\": 1, \"model\": \"gpt-4o-mini\", " +
               "\"choices\": [{\"index\": 0, \"delta\": " + delta + ", \"finish_reason\": " + finish + "}]}";
    }

}
