// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Client of Triton's OpenAI-compatible frontend, for llama.cpp models.
 * Model management goes through {@link TritonModelRepositoryClient}.
 *
 * @author ingridhabrekke
 * @author glebashnik
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

    float[] embed(String modelName, String text, Optional<Instant> deadline) {
        var response = post("/v1/embeddings", Map.of("model", modelName, "input", new String[] { text }), deadline);
        var embedding = response.path("data").path(0).path("embedding");
        if ( ! embedding.isArray())
            throw new TritonException("No embedding in the response of model " + modelName + ": " + response);
        var result = new float[embedding.size()];
        for (int i = 0; i < result.length; i++)
            result[i] = (float) embedding.get(i).asDouble();
        return result;
    }

    /** Returns the model's token ids of the text, without special tokens. */
    int[] tokenize(String modelName, String text, Optional<Instant> deadline) {
        // Triton's frontend routes the request by the model name.
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
     * @throws InvalidInputException if llama.cpp rejects the input, e.g. because it is longer than the context
     * @throws TimeoutException if the deadline passes
     * @throws OverloadException if Triton is loading or restarting the model, which is temporary
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
        var timeoutMillis = effectiveTimeout.toMillis();
        call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS);
        var startNanos = System.nanoTime();

        try (var response = call.execute()) {
            var responseBody = response.body() != null ? response.body().string() : "";
            if (response.isSuccessful()) return objectMapper.readTree(responseBody);
            // 503: Triton is loading or restarting the model.
            // 400, or 500 "too large to process", with {"error": {"message": ...}}: llama.cpp rejects the input.
            // 400 with {"detail": ...}: Triton's own error, e.g. an unknown model.
            if (response.code() == 503)
                throw new OverloadException("llama.cpp in Triton is unavailable, e.g. while loading or restarting the model: " + responseBody);
            var message = llamacppErrorMessage(responseBody);
            if (message.isPresent() &&
                (response.code() == 400 || (response.code() == 500 && message.get().contains("too large to process"))))
                throw new InvalidInputException("llama.cpp in Triton rejected the input: " + message.get());
            throw new TritonException(
                    "Request to " + path + " failed with status " + response.code() + ": " + responseBody);
        } catch (InterruptedIOException e) {
            // OkHttp throws this when the call timeout passes, and also on a connect timeout or an interrupt.
            if (System.nanoTime() - startNanos >= TimeUnit.MILLISECONDS.toNanos(timeoutMillis))
                throw new TimeoutException("Request to " + path + " timed out after " + timeoutMillis + " ms", e);
            throw new TritonException("Request to " + path + " failed: " + e.getMessage(), e);
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

    private static Optional<String> llamacppErrorMessage(String body) {
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
