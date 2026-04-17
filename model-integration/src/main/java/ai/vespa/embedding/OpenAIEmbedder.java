// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.HttpEmbedderConfig;
import ai.vespa.embedding.config.OpenaiEmbedderConfig;
import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Embedder using the OpenAI (compatible) embeddings API.
 *
 * @see <a href="https://developers.openai.com/api/reference/resources/embeddings/methods/create">OpenAI Embeddings API</a>
 * @author bjorncs
 */
@Beta
public class OpenAIEmbedder extends AbstractHttpEmbedder implements Embedder {

    private static final String ADA_002_MODEL = "text-embedding-ada-002";
    private static final int ADA_002_DIMENSIONS = 1536;

    private final OpenaiEmbedderConfig config;
    private final Embedder.Runtime runtime;
    private final Secret apiKey;

    @Inject
    public OpenAIEmbedder(OpenaiEmbedderConfig config, Embedder.Runtime runtime, Secrets secrets) {
        this(config, new HttpEmbedderConfig.Builder().build(), runtime, secrets);
    }

    OpenAIEmbedder(OpenaiEmbedderConfig config, HttpEmbedderConfig httpConfig,
                   Embedder.Runtime runtime, Secrets secrets) {
        super(httpConfig);
        this.config = config;
        this.runtime = runtime;
        this.apiKey = config.apiKeySecretRef().isEmpty() ? null : secrets.get(config.apiKeySecretRef());
        if (ADA_002_MODEL.equals(config.model()) && config.dimensions() != ADA_002_DIMENSIONS)
            throw new IllegalArgumentException(
                    "Model '%s' has a fixed output size of %d; configure dimensions=%d"
                            .formatted(ADA_002_MODEL, ADA_002_DIMENSIONS, ADA_002_DIMENSIONS));
    }

    @Override
    public List<Integer> embed(String text, Context context) {
        throw new UnsupportedOperationException(
                "OpenAI embedder only supports embed() with TensorType. Use embed(String, Context, TensorType) instead.");
    }

    @Override
    public Tensor embed(String text, Context context, TensorType targetType) {
        return embed(List.of(text), context, targetType).get(0);
    }

    @Override
    public List<Tensor> embed(List<String> texts, Context context, TensorType targetType) {
        long startTime = System.nanoTime();
        EmbeddingQuantization.validateTensorType(targetType, config.dimensions(), EmbeddingQuantization.Quantization.FLOAT);

        record CacheKey(String embedderId, List<String> texts) {}
        var cacheKey = new CacheKey(context.getEmbedderId(), texts);
        var response = context.computeCachedValueIfAbsent(cacheKey, () -> sendRequest(texts, context));

        if (response.usage != null) runtime.sampleSequenceLength(response.usage.totalTokens(), context);
        var tensors = toTensors(response, targetType);
        runtime.sampleEmbeddingLatency(Duration.ofNanos(System.nanoTime() - startTime).toMillis(), context);
        return tensors;
    }

    private EmbeddingResponse sendRequest(List<String> texts, Context context) {
        // ada-002 has a fixed 1536 output size and rejects the "dimensions" request field
        Integer dimensions = ADA_002_MODEL.equals(config.model()) ? null : config.dimensions();
        var json = toJson(new EmbeddingRequest(texts, config.model(), dimensions, "base64"));
        runtime.sampleRequestCount(context);
        var body = doHttpRequest(config.endpoint(), json, authHeaders(), context, runtime);
        return fromJson(body, EmbeddingResponse.class);
    }

    private Map<String, String> authHeaders() {
        return apiKey == null ? Map.of() : Map.of("Authorization", "Bearer " + apiKey.current());
    }

    private static List<Tensor> toTensors(EmbeddingResponse response, TensorType targetType) {
        var dim = targetType.dimensions().get(0);
        return response.data.stream()
                .sorted(Comparator.comparingInt(EmbeddingData::index))
                .map(d -> {
                    if (d.embedding() == null)
                        throw new IllegalStateException(
                                "Embedding at index %d is null in API response — the provider may not support base64 encoding.".formatted(d.index()));
                    return EmbeddingQuantization.decodeBase64FloatTensor(d.embedding(), dim.name(), targetType.valueType(), dim.size().orElseThrow());
                })
                .toList();
    }

    // ===== DTOs =====

    private record EmbeddingRequest(
            @JsonProperty("input") List<String> input,
            @JsonProperty("model") String model,
            @JsonProperty("dimensions") @JsonInclude(JsonInclude.Include.NON_NULL) Integer dimensions,
            @JsonProperty("encoding_format") String encodingFormat) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(
            @JsonProperty("data") List<EmbeddingData> data,
            @JsonProperty("usage") Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingData(
            @JsonProperty("embedding") String embedding,
            @JsonProperty("index") int index) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(@JsonProperty("total_tokens") int totalTokens) {}

}
