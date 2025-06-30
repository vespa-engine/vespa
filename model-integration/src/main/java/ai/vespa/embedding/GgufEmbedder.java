// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.config.GgufEmbedderConfig;
import ai.vespa.modelintegration.utils.ModelPathHelper;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;
import de.kherud.llama.args.PoolingType;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Embedder using GGUF file format through llama.cpp
 *
 * @author bjorncs
 */
public class GgufEmbedder extends AbstractComponent implements Embedder {

    private static final Logger log = Logger.getLogger(GgufEmbedder.class.getName());
    private final LlamaModel model;
    private final int maxPromptTokens;
    private final String prependQuery;
    private final String prependDocument;
    private final boolean normalize;

    public static class Exception extends RuntimeException {
        public Exception(Throwable cause) { super(cause); }
    }

    @Inject
    public GgufEmbedder(GgufEmbedderConfig config, ModelPathHelper helper) {
        log.fine(() -> "Config: %s".formatted(config));
        var modelPath = helper.getModelPathResolvingIfNecessary(config.embeddingModelReference()).toString();
        var modelParams = new ModelParameters()
                .enableEmbedding()
                .setParallel(config.parallel())
                .setModel(modelPath)
                .setGpuLayers(config.gpuLayers());
        if (config.continuousBatching()) modelParams.enableContBatching();
        if (config.poolingType() != GgufEmbedderConfig.PoolingType.Enum.UNSPECIFIED)
            modelParams.setPoolingType(PoolingType.valueOf(config.poolingType().name()));
        if (config.physicalMaxBatchSize() > 0) modelParams.setUbatchSize(config.physicalMaxBatchSize());
        if (config.logicalMaxBatchSize() > 0) modelParams.setBatchSize(config.logicalMaxBatchSize());
        if (config.contextSize() > 0) modelParams.setCtxSize(config.contextSize());
        if (config.seed() > -1) modelParams.setSeed(config.seed());
        if (config.threads() != 0) modelParams.setThreads(calculateThreadCount(config.threads()));
        if (config.batchThreads() != 0) modelParams.setThreadsBatch(calculateThreadCount(config.batchThreads()));
        if (!log.isLoggable(Level.FINE)) modelParams.disableLog();
        model = new LlamaModel(modelParams);
        maxPromptTokens = config.maxPromptTokens();
        prependQuery = config.prependQuery();
        prependDocument = config.prependDocument();
        normalize = config.normalize();
    }

    @Override
    public Tensor embed(String text, Context context, TensorType tensorType) {
        var prompt = prependAndTruncatePrompt(text, context);
        record CacheKey(String embedderId, String text){}
        var cacheKey = new CacheKey(context.getEmbedderId(), prompt);
        var rawEmbedding = context.computeCachedValueIfAbsent(cacheKey, () -> generateRawEmbedding(prompt));
        if (tensorType.dimensions().size() != 1) {
            throw new IllegalArgumentException(
                    "Error in embedding to type '%s': should only have one dimension.".formatted(tensorType));
        }
        var dimension = tensorType.dimensions().get(0);
        if (!dimension.isIndexed()) {
            throw new IllegalArgumentException(
                    "Error in embedding to type '%s': dimension should be indexed.".formatted(tensorType));
        }
        var dimensionSize = dimension.size().orElseThrow();
        if (rawEmbedding.length != dimensionSize) {
            throw new IllegalArgumentException(
                    "Error in embedding to type '%s': expected dimension size %d, but got %d.".formatted(
                            tensorType, dimensionSize, rawEmbedding.length));
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
        return Arrays.stream(wrapLlamaException(() -> model.encode(text)))
                .boxed().toList();
    }

    @Override
    public String decode(List<Integer> tokens, Context context) {
        return wrapLlamaException(() -> model.decode(tokens.stream().mapToInt(Integer::intValue).toArray()));
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
        if (!prependQuery.isBlank() && context.getDestination().startsWith("query")) {
            text = prependQuery + " " + text;
        } else if (!prependDocument.isBlank()) {
            text = prependDocument + " " + text;
        }
        if (maxPromptTokens <= 0) return text;
        var tokens = model.encode(text);
        var maxTruncatedLength = maxPromptTokens - 2; // Reserve space for start and end token
        if (tokens.length <= maxTruncatedLength) return text;
        log.fine(() -> "Truncating prompt from %d to %d tokens".formatted(tokens.length, maxTruncatedLength));
        var truncatedTokens = Arrays.copyOfRange(tokens, 0, maxTruncatedLength);
        return model.decode(truncatedTokens);
    }

    private float[] generateRawEmbedding(String prompt) {
        try {
            return wrapLlamaException(() -> model.embed(prompt));
        } catch (GgufEmbedder.Exception e) {
            var cause = e.getCause();
            if (cause == null) throw e;
            if (cause.getClass().getName().endsWith("de.kherud.llama.LlamaException") // Package-private exception
                    && cause.getMessage().contains("input is too large to process")) {
                // Illegal input must be propagated as IllegalArgumentException
                throw new IllegalArgumentException(
                        "Input text is too large (prompt UTF-16 length: %d). Either set max prompt tokens or adjust batch/context size."
                                .formatted(prompt.length()),
                        cause);
            }
            throw e;
        }
    }

    private static <T> T wrapLlamaException(Supplier<T> supplier) {
        try {
            return supplier.get();
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
