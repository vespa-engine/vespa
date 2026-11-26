// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.llamacpp;

import java.util.Optional;

/**
 * How llama.cpp runs a GGUF embedding model. A value that is not positive leaves llama.cpp's default,
 * except gpuLayers, where 0 means CPU only, and seed, where a negative value means a random seed.
 *
 * @param parallel the number of sequences decoded in parallel, at least 1
 * @param contextSize the total context size shared by the parallel sequences, 0 to use the model's
 * @param logicalMaxBatchSize llama.cpp's batch size
 * @param physicalMaxBatchSize llama.cpp's micro-batch size, which must hold a whole input of an embedding model
 * @param gpuLayers the maximum number of model layers to run on a GPU, negative for the runtime's default
 * @param pooling how token embeddings are pooled, empty to use the model's default
 * @param seed the random number generator seed
 * @param threads the number of threads used for a micro-batch of one token
 * @param batchThreads the number of threads used for a micro-batch of several tokens
 * @param continuousBatching whether to batch sequences continuously
 * @param imageMinTokens the minimum number of tokens an image becomes, with a multimodal projector
 * @param imageMaxTokens the maximum number of tokens an image becomes, with a multimodal projector
 *
 * @author ingridhabrekke
 */
public record LlamacppOptions(int parallel, int contextSize, int logicalMaxBatchSize, int physicalMaxBatchSize,
                           int gpuLayers, Optional<Pooling> pooling, int seed, int threads, int batchThreads,
                           boolean continuousBatching, int imageMinTokens, int imageMaxTokens) {

    public enum Pooling { NONE, MEAN, CLS, LAST, RANK }

    /** Options of a model without a multimodal projector, or with the projector's default image tokens. */
    public LlamacppOptions(int parallel, int contextSize, int logicalMaxBatchSize, int physicalMaxBatchSize,
                           int gpuLayers, Optional<Pooling> pooling, int seed, int threads, int batchThreads,
                           boolean continuousBatching) {
        this(parallel, contextSize, logicalMaxBatchSize, physicalMaxBatchSize, gpuLayers, pooling, seed, threads,
             batchThreads, continuousBatching, 0, 0);
    }

    public LlamacppOptions {
        if (parallel < 1) throw new IllegalArgumentException("parallel must be at least 1, got " + parallel);
    }

}
