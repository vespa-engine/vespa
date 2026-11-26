// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.modelintegration.llamacpp.LlamacppEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.InvalidInputException;
import com.yahoo.language.process.OverloadException;
import com.yahoo.language.process.TimeoutException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the requests TritonLlamacppClient sends and how it reads llama-server's responses and errors.
 * The response bodies are as llama-server returns them through Triton's OpenAI frontend.
 *
 * @author ingridhabrekke
 */
class TritonLlamacppClientTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private MockWebServer server;
    private TritonLlamacppClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new TritonLlamacppClient(server.url("/").toString(), Duration.ofSeconds(10));
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        server.shutdown();
    }

    @Test
    void embeds_text_and_image_parts_in_order_as_one_input() throws Exception {
        server.enqueue(json(200, """
                {"model":"m","object":"list","data":[{"embedding":[0.6,0.8],"index":0,"object":"embedding"}]}"""));
        byte[] png = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2 };
        var parts = List.<LlamacppEvaluator.Part>of(new LlamacppEvaluator.Part.Text("passage: "),
                            new LlamacppEvaluator.Part.Image(new Embedder.Image(png)),
                            new LlamacppEvaluator.Part.Text(" A title"));

        assertArrayEquals(new float[] { 0.6f, 0.8f }, client.embed("m", parts, Optional.empty()));

        var request = server.takeRequest();
        assertEquals("/v1/embeddings", request.getPath());
        assertEquals(objectMapper.readTree("""
                {"model":"m","input":[{"content":[
                  {"type":"text","text":"passage: "},
                  {"type":"image_url","image_url":{"url":"data:image/png;base64,iVBORw0KGgoBAg=="}},
                  {"type":"text","text":" A title"}]}]}"""),
                     objectMapper.readTree(request.getBody().readUtf8()));
    }

    @Test
    void embeds_one_text_with_llama_servers_default_normalization() throws Exception {
        server.enqueue(json(200, """
                {"model":"m","object":"list","usage":{"prompt_tokens":2,"total_tokens":2},
                 "data":[{"embedding":[-1.5,1.25,0.5],"index":0,"object":"embedding"}]}"""));

        assertArrayEquals(new float[] { -1.5f, 1.25f, 0.5f }, client.embed("m", "hello world", Optional.empty()));

        var request = server.takeRequest();
        assertEquals("/v1/embeddings", request.getPath());
        assertEquals(objectMapper.readTree("""
                {"model":"m","input":["hello world"]}"""),
                     objectMapper.readTree(request.getBody().readUtf8()));
    }

    @Test
    void tokenizes_without_special_tokens() throws Exception {
        server.enqueue(json(200, "{\"tokens\":[14990,1879]}"));

        assertArrayEquals(new int[] { 14990, 1879 }, client.tokenize("m", "hello world", Optional.empty()));

        var request = server.takeRequest();
        assertEquals("/tokenize", request.getPath());
        assertEquals(objectMapper.readTree("""
                {"model":"m","content":"hello world","add_special":false}"""),
                     objectMapper.readTree(request.getBody().readUtf8()));
    }

    @Test
    void detokenizes() throws Exception {
        server.enqueue(json(200, "{\"content\":\"hello world\"}"));

        assertEquals("hello world", client.detokenize("m", new int[] { 14990, 1879 }, Optional.empty()));

        var request = server.takeRequest();
        assertEquals("/detokenize", request.getPath());
        assertEquals(objectMapper.readTree("{\"model\":\"m\",\"tokens\":[14990,1879]}"),
                     objectMapper.readTree(request.getBody().readUtf8()));
    }

    @Test
    void input_rejected_by_llama_server_is_invalid_input() {
        server.enqueue(json(400, """
                {"error":{"code":400,"message":"request (23891 tokens) exceeds the available context size (4096 tokens), try increasing it",
                 "type":"exceed_context_size_error","n_prompt_tokens":23891,"n_ctx":4096}}"""));

        var e = assertThrows(InvalidInputException.class, () -> client.embed("m", "long text", Optional.empty()));
        assertTrue(e.getMessage().contains("exceeds the available context size"), e.getMessage());
    }

    @Test
    void input_longer_than_the_micro_batch_is_invalid_input() {
        server.enqueue(json(500, """
                {"error":{"code":500,"message":"input (10002 tokens) is too large to process. increase the physical batch size (current batch size: 128)",
                 "type":"server_error"}}"""));

        var e = assertThrows(InvalidInputException.class, () -> client.embed("m", "long text", Optional.empty()));
        assertTrue(e.getMessage().contains("too large to process"), e.getMessage());
    }

    @Test
    void other_server_errors_are_triton_failures() {
        server.enqueue(json(500, "{\"error\":{\"code\":500,\"message\":\"Loading model\",\"type\":\"unavailable_error\"}}"));

        assertThrows(TritonException.class, () -> client.embed("m", "text", Optional.empty()));
    }

    @Test
    void model_that_triton_does_not_serve_with_llamacpp_is_a_triton_failure() {
        // Triton's stock frontend handles requests for other models, and rejects a list of texts.
        server.enqueue(json(422, "{\"detail\":[{\"type\":\"string_type\",\"msg\":\"Input should be a valid string\"}]}"));

        var e = assertThrows(TritonException.class, () -> client.embed("m", "text", Optional.empty()));
        assertTrue(e.getMessage().contains("422"), e.getMessage());
    }

    @Test
    void model_that_triton_has_not_loaded_is_a_triton_failure() {
        // Triton's frontend answers with 400 for a model it does not have, e.g. after Triton restarted.
        server.enqueue(json(400, "{\"detail\":\"Unknown model: m\"}"));

        var e = assertThrows(TritonException.class, () -> client.embed("m", "text", Optional.empty()));
        assertTrue(e.getMessage().contains("Unknown model"), e.getMessage());
    }

    @Test
    void unavailable_engine_is_overload() {
        // The backend answers 503 while it loads or restarts llama-server, e.g. in its backoff after a crash.
        server.enqueue(json(503, "{\"error\":{\"code\":503,\"message\":\"Loading model\",\"type\":\"unavailable_error\"}}"));

        assertThrows(OverloadException.class, () -> client.embed("m", "text", Optional.empty()));
    }

    @Test
    void expired_deadline_fails_without_a_request() {
        assertThrows(TimeoutException.class, () -> client.embed("m", "text", Optional.of(Instant.now().minusMillis(5))));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void slow_response_times_out() {
        server.enqueue(json(200, "{\"tokens\":[1]}").setHeadersDelay(2, TimeUnit.SECONDS));

        assertThrows(TimeoutException.class, () -> client.tokenize("m", "text", Optional.of(Instant.now().plusMillis(200))));
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body);
    }

}
