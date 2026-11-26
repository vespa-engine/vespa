// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.llamacpp;

import com.yahoo.component.AbstractComponent;
import com.yahoo.text.Text;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;
import de.kherud.llama.args.PoolingType;

import java.time.Instant;
import java.util.Optional;

/**
 * Runs GGUF embedding models with llama.cpp in the container, through its Java bindings.
 * Each evaluator loads its own copy of the model.
 *
 * @author bjorncs
 * @author ingridhabrekke
 */
public class EmbeddedLlamacppRuntime extends AbstractComponent implements LlamacppRuntime {

    @Override
    public LlamacppEvaluator evaluatorOf(String modelPath, LlamacppOptions options) {
        return new Evaluator(new LlamaModel(modelParameters(modelPath, options)));
    }

    static ModelParameters modelParameters(String modelPath, LlamacppOptions options) {
        var modelParams = new ModelParameters()
                .enableEmbedding()
                .setParallel(options.parallel())
                .setModel(modelPath);
        // Without --gpu-layers, llama.cpp offloads as many layers as fit in GPU memory, if the loaded
        // native library supports GPUs and finds one.
        if (options.gpuLayers() >= 0) modelParams.setGpuLayers(options.gpuLayers());
        if (options.continuousBatching()) modelParams.enableContBatching();
        options.pooling().ifPresent(pooling -> modelParams.setPoolingType(PoolingType.valueOf(pooling.name())));
        if (options.physicalMaxBatchSize() > 0) modelParams.setUbatchSize(options.physicalMaxBatchSize());
        if (options.logicalMaxBatchSize() > 0) modelParams.setBatchSize(options.logicalMaxBatchSize());
        if (options.contextSize() > 0) modelParams.setCtxSize(options.contextSize());
        if (options.seed() > -1) modelParams.setSeed(options.seed());
        if (options.threads() > 0) modelParams.setThreads(options.threads());
        if (options.batchThreads() > 0) modelParams.setThreadsBatch(options.batchThreads());
        return modelParams;
    }

    private record Evaluator(LlamaModel model) implements LlamacppEvaluator {

        @Override
        public float[] embed(String text, Optional<Instant> deadline) {
            try {
                return model.embed(text);
            } catch (RuntimeException e) {
                if (e.getClass().getName().endsWith("de.kherud.llama.LlamaException") // Package-private exception
                    && e.getMessage() != null && e.getMessage().contains("too large to process")) {
                    // Illegal input must be propagated as IllegalArgumentException
                    throw new IllegalArgumentException(
                            Text.format("Input text is too large (prompt UTF-16 length: %d). " +
                                        "Either set max prompt tokens or adjust batch/context size.", text.length()),
                            e);
                }
                throw e;
            }
        }

        @Override
        public int[] tokenize(String text, Optional<Instant> deadline) { return model.encode(text); }

        @Override
        public String detokenize(int[] tokens, Optional<Instant> deadline) { return model.decode(tokens); }

        @Override
        public void close() { model.close(); }

    }

}
