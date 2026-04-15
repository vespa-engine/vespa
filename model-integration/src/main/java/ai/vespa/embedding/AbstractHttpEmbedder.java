// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.HttpEmbedderConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.component.AbstractComponent;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.InvalidInputException;
import com.yahoo.language.process.OverloadException;
import com.yahoo.language.process.TimeoutException;
import okhttp3.ConnectionPool;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Thin HTTP scaffolding for embedding providers. Owns the {@link OkHttpClient} lifecycle and
 * exposes {@link #doHttpRequest} for POSTing JSON with per-request timeout and automatic 5xx retries.
 * Also provides shared JSON (de)serialization helpers for subclasses.
 *
 * @author bjorncs
 */
public abstract class AbstractHttpEmbedder extends AbstractComponent {

    private static final Logger log = Logger.getLogger(AbstractHttpEmbedder.class.getName());
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    protected static final ObjectMapper objectMapper = new ObjectMapper();

    private final Duration defaultTimeout;
    private final OkHttpClient httpClient;

    protected AbstractHttpEmbedder() {
        this(new HttpEmbedderConfig.Builder().build());
    }

    protected AbstractHttpEmbedder(HttpEmbedderConfig config) {
        this.defaultTimeout = Duration.ofMillis(config.timeout());
        this.httpClient = createHttpClient(config.maxRetries(), defaultTimeout);
    }

    /**
     * POST the given JSON payload to the endpoint with the given headers and return the response body.
     * Non-2xx responses are mapped to appropriate exceptions (429 → {@link OverloadException},
     * 400 → {@link InvalidInputException}, 401/403 → authentication {@link RuntimeException}, else → generic
     * {@link RuntimeException}) and request-failure metrics are sampled on the given runtime.
     *
     * @return the response body for a 2xx response
     * @throws TimeoutException if the context deadline is already expired, or the call times out
     * @throws RuntimeException on I/O failures other than timeouts, or on a non-retryable non-2xx response
     */
    protected String doHttpRequest(String endpoint, String jsonPayload, Map<String, String> headers,
                                   Embedder.Context context, Embedder.Runtime runtime) {
        var timeoutMs = calculateTimeoutMs(context);
        var builder = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(jsonPayload, JSON_MEDIA_TYPE));
        headers.forEach(builder::header);
        var httpRequest = builder.build();

        log.fine(() -> "Embedding API request: " + jsonPayload);
        var call = httpClient.newCall(httpRequest);
        call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS);

        try (var response = call.execute()) {
            var body = response.body() != null ? response.body().string() : "";
            log.fine(() -> "Embedding API response with code %d: %s".formatted(response.code(), body));
            if (response.isSuccessful()) return body;
            runtime.sampleRequestFailure(context, response.code());
            throw switch (response.code()) {
                case 429 -> new OverloadException("Embedding API rate limited (429)");
                case 401, 403 -> new RuntimeException(
                        "Embedding API authentication failed (%d). Please check your API key: %s"
                                .formatted(response.code(), body));
                case 400 -> new InvalidInputException(
                        "Embedding API bad request (400): " + parseErrorMessage(body).orElse(body));
                default -> new RuntimeException(
                        "Embedding API request failed with status %d: %s".formatted(response.code(), body));
            };
        } catch (InterruptedIOException e) {
            runtime.sampleRequestFailure(context, 0);
            throw new TimeoutException("Embedding API call timed out after %dms".formatted(timeoutMs), e);
        } catch (IOException e) {
            runtime.sampleRequestFailure(context, 0);
            throw new RuntimeException("Embedding API call failed: " + e.getMessage(), e);
        }
    }

    /** Serialize an object to JSON. */
    protected static String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize embedder request", e);
        }
    }

    /** Deserialize JSON to the given type. */
    protected static <T> T fromJson(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse embedder response", e);
        }
    }

    /**
     * Parses an error message from a provider response. Handles both the OpenAI/Mistral shape
     * ({@code {"error":{"message":"..."}}}) and the VoyageAI shape ({@code {"detail":"..."}}).
     */
    private static Optional<String> parseErrorMessage(String body) {
        try {
            var openAiStyle = objectMapper.readValue(body, OpenAIStyleError.class);
            if (openAiStyle.error != null && openAiStyle.error.message != null)
                return Optional.of(openAiStyle.error.message);
            var detailStyle = objectMapper.readValue(body, DetailStyleError.class);
            if (detailStyle.detail != null && !detailStyle.detail.isEmpty())
                return Optional.of(detailStyle.detail);
        } catch (JsonProcessingException e) {
            log.fine(() -> "Failed to parse error response as JSON: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void deconstruct() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        super.deconstruct();
    }

    private long calculateTimeoutMs(Embedder.Context context) {
        long remainingMs = context.getDeadline()
                .map(d -> d.timeRemaining().toMillis())
                .orElse(defaultTimeout.toMillis());
        if (remainingMs <= 0)
            throw new TimeoutException("Request deadline exceeded before embedding API call");
        return remainingMs;
    }

    private static OkHttpClient createHttpClient(int maxRetries, Duration timeout) {
        return new OkHttpClient.Builder()
                .addInterceptor(new RetryInterceptor(maxRetries))
                .retryOnConnectionFailure(true)
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .connectionPool(new ConnectionPool(10, 1, TimeUnit.MINUTES))
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAIStyleError(@JsonProperty("error") ErrorDetail error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorDetail(@JsonProperty("message") String message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DetailStyleError(@JsonProperty("detail") String detail) {}

    private static class RetryInterceptor implements Interceptor {
        static final long RETRY_DELAY_MS = 100;
        private final int maxRetries;

        RetryInterceptor(int maxRetries) { this.maxRetries = maxRetries; }

        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            var request = chain.request();
            int lastStatusCode = 0;
            var lastResponseBody = "";

            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    var response = chain.proceed(request);
                    int code = response.code();
                    boolean shouldRetry = code == 500 || code == 502 || code == 503 || code == 504;
                    if (response.isSuccessful() || !shouldRetry) return response;

                    lastStatusCode = code;
                    if (attempt < maxRetries) {
                        response.close();
                        int retryNumber = attempt + 1;
                        log.fine(() -> "Embedding API server error (%d). Retry %d of %d after %dms"
                                .formatted(code, retryNumber, maxRetries, RETRY_DELAY_MS));
                        Thread.sleep(RETRY_DELAY_MS);
                    } else {
                        if (response.body() != null) lastResponseBody = response.body().string();
                        response.close();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", e);
                }
            }

            throw new IOException("Max retries exceeded for embedding API (%d). Last response: %d - %s"
                    .formatted(maxRetries, lastStatusCode, lastResponseBody));
        }
    }
}
