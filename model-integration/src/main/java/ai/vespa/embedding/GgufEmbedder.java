// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.GgufEmbedderConfig;
import ai.vespa.modelintegration.llamacpp.EmbeddedLlamacppRuntime;
import ai.vespa.modelintegration.llamacpp.LlamacppEvaluator;
import ai.vespa.modelintegration.llamacpp.LlamacppOptions;
import ai.vespa.modelintegration.llamacpp.LlamacppRuntime;
import ai.vespa.modelintegration.utils.ModelPathHelper;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.OverloadException;
import com.yahoo.language.process.TimeoutException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.text.Text;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Embedder using GGUF file format through llama.cpp.
 * The model runs in the container, or in a Triton Inference Server sidecar when the config model
 * provides a Triton llama.cpp runtime.
 *
 * @author bjorncs
 */
public class GgufEmbedder extends AbstractComponent implements Embedder {

    private static final Logger log = Logger.getLogger(GgufEmbedder.class.getName());
    private final LlamacppEvaluator model;
    private final int maxPromptTokens;
    private final TextPrepender prepender;
    private final boolean normalize;

    public static class Exception extends RuntimeException {
        public Exception(Throwable cause) { super(cause.getMessage(), cause); }
    }

    /**
     * Runs the model in the llama.cpp runtime of the container cluster, if it has one, else in the container.
     * The runtime comes as a registry because it is optional: the config model adds a runtime component,
     * e.g. TritonLlamacppRuntime, only to some clusters, and injecting a single component would fail without one.
     */
    @Inject
    public GgufEmbedder(GgufEmbedderConfig config, ModelPathHelper helper, ComponentRegistry<LlamacppRuntime> runtimes) {
        this(config, helper, runtimeOf(runtimes, EmbeddedLlamacppRuntime::new));
    }

    /** Returns the only runtime in the registry, or the fallback if it has none. */
    static LlamacppRuntime runtimeOf(ComponentRegistry<LlamacppRuntime> runtimes, Supplier<LlamacppRuntime> fallback) {
        var runtimesById = runtimes.allComponentsById();
        if (runtimesById.size() > 1)
            throw new IllegalArgumentException("Expected at most one llama.cpp runtime component, but got " +
                                               runtimesById.keySet().stream().map(ComponentId::stringValue).sorted().toList());
        return runtimesById.isEmpty() ? fallback.get() : runtimesById.values().iterator().next();
    }

    public GgufEmbedder(GgufEmbedderConfig config, ModelPathHelper helper) {
        this(config, helper, new EmbeddedLlamacppRuntime());
    }

    GgufEmbedder(GgufEmbedderConfig config, ModelPathHelper helper, LlamacppRuntime runtime) {
        log.fine(() -> Text.format("Config: %s", config));
        var modelPath = helper.getModelPathResolvingIfNecessary(config.embeddingModelReference()).toString();
        model = runtime.evaluatorOf(modelPath, options(config));
        maxPromptTokens = config.maxPromptTokens();
        prepender = new TextPrepender(config.prependQuery(), config.prependDocument());
        normalize = config.normalize();
    }

    static LlamacppOptions options(GgufEmbedderConfig config) {
        var pooling = config.poolingType() == GgufEmbedderConfig.PoolingType.Enum.UNSPECIFIED
                ? Optional.<LlamacppOptions.Pooling>empty()
                : Optional.of(LlamacppOptions.Pooling.valueOf(config.poolingType().name()));
        return new LlamacppOptions(config.parallel(),
                                config.contextSize(),
                                config.logicalMaxBatchSize(),
                                config.physicalMaxBatchSize(),
                                config.gpuLayers(),
                                pooling,
                                config.seed(),
                                config.threads() != 0 ? calculateThreadCount(config.threads()) : 0,
                                config.batchThreads() != 0 ? calculateThreadCount(config.batchThreads()) : 0,
                                config.continuousBatching());
    }

    @Override
    public Tensor embed(String text, Context context, TensorType tensorType) {
        var prompt = prependAndTruncatePrompt(text, context);
        record CacheKey(String embedderId, String text){}
        var cacheKey = new CacheKey(context.getEmbedderId(), prompt);
        var rawEmbedding = context.computeCachedValueIfAbsent(cacheKey, () -> generateRawEmbedding(prompt, context));
        if (tensorType.dimensions().size() != 1) {
            throw new IllegalArgumentException(
                    Text.format("Error in embedding to type '%s': should only have one dimension.", tensorType));
        }
        var dimension = tensorType.dimensions().get(0);
        if (!dimension.isIndexed()) {
            throw new IllegalArgumentException(
                    Text.format("Error in embedding to type '%s': dimension should be indexed.", tensorType));
        }
        var dimensionSize = dimension.size().orElseThrow();
        if (rawEmbedding.length != dimensionSize) {
            throw new IllegalArgumentException(
                    Text.format("Error in embedding to type '%s': expected dimension size %d, but got %d.", tensorType, dimensionSize, rawEmbedding.length));
        }
        var builder = Tensor.Builder.of(tensorType);
        for (int i = 0; i < dimensionSize; i++) {
            builder.cell(rawEmbedding[i], i);
        }
        var embedding = builder.build();
        return normalize ? EmbeddingNormalizer.normalize(embedding, tensorType) : embedding;
    }

    @Override
    public List<Integer> embed(String text, Context context) {
        return Arrays.stream(wrapLlamaException(() -> model.tokenize(text, deadline(context))))
                .boxed().toList();
    }

    @Override
    public String decode(List<Integer> tokens, Context context) {
        var tokenIds = tokens.stream().mapToInt(Integer::intValue).toArray();
        return wrapLlamaException(() -> model.detokenize(tokenIds, deadline(context)));
    }

    @Override
    public void deconstruct() {
        model.close();
    }

    /**
     * Add prefix to prompt if configured, then do naive truncation of the prompt to the maximum number of tokens.
     * Tokens are assumed to be independent and that the token sequence can be safely truncated at any position.
     */
    private String prependAndTruncatePrompt(String text, Context context) {
        var prompt = prepender.prepend(text, context);
        if (maxPromptTokens <= 0) return prompt;
        var tokens = wrapLlamaException(() -> model.tokenize(prompt, deadline(context)));
        var maxTruncatedLength = maxPromptTokens - 2; // Reserve space for start and end token
        if (tokens.length <= maxTruncatedLength) return prompt;
        log.fine(() -> Text.format("Truncating prompt from %d to %d tokens", tokens.length, maxTruncatedLength));
        var truncatedTokens = Arrays.copyOfRange(tokens, 0, maxTruncatedLength);
        return wrapLlamaException(() -> model.detokenize(truncatedTokens, deadline(context)));
    }

    private float[] generateRawEmbedding(String prompt, Context context) {
        return wrapLlamaException(() -> model.embed(prompt, deadline(context)));
    }

    // The request deadline, or empty to use the runtime's default timeout. A runtime in the container ignores it.
    private static Optional<Instant> deadline(Context context) {
        return context.getDeadline().map(deadline -> deadline.asInstant());
    }

    // Illegal input, timeouts and overload keep their types, which callers act on.
    private static <T> T wrapLlamaException(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException | TimeoutException | OverloadException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new GgufEmbedder.Exception(e);
        }
    }

    /**
     * Calculates the number of threads to use based on the configuration value.
     * - If value > 0, use the absolute value.
     * - If value < 0, use as a ratio of available CPU cores.
     */
    private static int calculateThreadCount(double configValue) {
        if (configValue > 0) return (int) configValue; // Use absolute value
        int availableProcessors = java.lang.Runtime.getRuntime().availableProcessors();
        return (int) Math.round(availableProcessors * Math.abs(configValue));
    }
}
