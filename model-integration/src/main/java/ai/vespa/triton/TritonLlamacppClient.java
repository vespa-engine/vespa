// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.llamacpp.LlamacppEvaluator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.InvalidInputException;
import com.yahoo.language.process.OverloadException;
import com.yahoo.language.process.TimeoutException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Client of Triton's OpenAI-compatible frontend, which relays requests for a llama.cpp model
 * to the model's llama-server. Model management goes through {@link TritonModelRepositoryClient}.
 *
 * @author ingridhabrekke
 */
class TritonLlamacppClient implements AutoCloseable {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String endpoint;
    private final Duration defaultTimeout;
    private final OkHttpClient httpClient;

    TritonLlamacppClient(TritonConfig config) {
        this(config.llamacppEndpoint(), Duration.ofMillis(config.timeout()));
    }

    TritonLlamacppClient(String endpoint, Duration defaultTimeout) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.defaultTimeout = defaultTimeout;
        // Each call sets its own deadline; the client's timeouts would otherwise cut long calls short.
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ZERO)
                .writeTimeout(Duration.ZERO)
                .build();
    }

    /**
     * Returns the model's embedding of the text.
     * llama-server normalizes pooled embeddings to unit length (L2) by default, as LlamacppEvaluator.embed requires.
     */
    float[] embed(String modelName, String text, Optional<Instant> deadline) {
        var response = post("/v1/embeddings", Map.of("model", modelName, "input", new String[] { text }), deadline);
        return embeddingOf(response, modelName);
    }

    /**
     * Returns the model's embedding of the parts, as one input in llama-server's multimodal format.
     * llama-server applies no template, so the parts are read in the given order.
     */
    float[] embed(String modelName, List<LlamacppEvaluator.Part> parts, Optional<Instant> deadline) {
        var content = parts.stream().map(TritonLlamacppClient::contentOf).toList();
        var response = post("/v1/embeddings", Map.of("model", modelName, "input", List.of(Map.of("content", content))), deadline);
        return embeddingOf(response, modelName);
    }

    private static float[] embeddingOf(JsonNode response, String modelName) {
        var embedding = response.path("data").path(0).path("embedding");
        if ( ! embedding.isArray())
            throw new TritonException("No embedding in the response of model " + modelName + ": " + response);
        var result = new float[embedding.size()];
        for (int i = 0; i < result.length; i++)
            result[i] = (float) embedding.get(i).asDouble();
        return result;
    }

    private static Map<String, Object> contentOf(LlamacppEvaluator.Part part) {
        if (part instanceof LlamacppEvaluator.Part.Text text) return Map.of("type", "text", "text", text.text());
        var image = ((LlamacppEvaluator.Part.Image) part).image();
        return Map.of("type", "image_url", "image_url", Map.of("url", dataUrl(image)));
    }

    private static String dataUrl(Embedder.Image image) {
        var bytes = new byte[image.data().remaining()];
        image.data().get(bytes);
        return "data:" + image.mediaType() + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    /** Returns the model's token ids of the text, without special tokens. */
    int[] tokenize(String modelName, String text, Optional<Instant> deadline) {
        // Triton's frontend routes the request by the model name. llama-server itself ignores it.
        var response = post("/tokenize", Map.of("model", modelName, "content", text, "add_special", false), deadline);
        var tokens = response.path("tokens");
        if ( ! tokens.isArray())
            throw new TritonException("No tokens in the response of model " + modelName + ": " + response);
        var result = new int[tokens.size()];
        for (int i = 0; i < result.length; i++)
            result[i] = tokens.get(i).asInt();
        return result;
    }

    String detokenize(String modelName, int[] tokens, Optional<Instant> deadline) {
        var response = post("/detokenize", Map.of("model", modelName, "tokens", tokens), deadline);
        var content = response.path("content");
        if ( ! content.isTextual())
            throw new TritonException("No content in the response of model " + modelName + ": " + response);
        return content.asText();
    }

    /**
     * Posts the body as JSON and returns the response.
     *
     * @throws InvalidInputException if llama-server rejects the input, e.g. because it is longer than the context
     * @throws TimeoutException if the deadline passes
     * @throws OverloadException if Triton or llama-server is loading or restarting the model, which is temporary
     * @throws TritonException on any other failure, e.g. a model that Triton has not loaded
     */
    private JsonNode post(String path, Map<String, Object> body, Optional<Instant> deadline) {
        var effectiveTimeout = deadline.map(d -> Duration.between(Instant.now(), d)).orElse(defaultTimeout);
        if (effectiveTimeout.toMillis() <= 0)
            throw new TimeoutException("Request deadline exceeded before request to " + path);

        var request = new Request.Builder()
                .url(endpoint + path)
                .post(RequestBody.create(toJson(body), JSON))
                .build();
        var call = httpClient.newCall(request);
        call.timeout().timeout(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);

        try (var response = call.execute()) {
            var responseBody = response.body() != null ? response.body().string() : "";
            if (response.isSuccessful()) return objectMapper.readTree(responseBody);
            // 503: llama-server is loading or restarting.
            // 400, or 500 "too large to process", with {"error": {"message": ...}}: llama-server rejects the input.
            // 400 with {"detail": ...}: Triton's own error, e.g. an unknown model, which the evaluator reloads.
            if (response.code() == 503)
                throw new OverloadException("llama.cpp in Triton is unavailable, e.g. while loading or restarting the model: " + responseBody);
            var message = llamaServerErrorMessage(responseBody);
            if (message.isPresent() &&
                (response.code() == 400 || (response.code() == 500 && message.get().contains("too large to process"))))
                throw new InvalidInputException("llama.cpp in Triton rejected the input: " + message.get());
            throw new TritonException(
                    "Request to " + path + " failed with status " + response.code() + ": " + responseBody);
        } catch (InterruptedIOException e) {
            throw new TimeoutException("Request to " + path + " timed out after " + effectiveTimeout.toMillis() + " ms", e);
        } catch (IOException e) {
            throw new TritonException("Request to " + path + " failed: " + e.getMessage(), e);
        }
    }

    private static String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize request", e);
        }
    }

    /** Returns the message of an OpenAI-style error from llama-server, or empty if the body is not one. */
    private static Optional<String> llamaServerErrorMessage(String body) {
        try {
            var message = objectMapper.readTree(body).path("error").path("message");
            return message.isTextual() ? Optional.of(message.asText()) : Optional.empty();
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

}
