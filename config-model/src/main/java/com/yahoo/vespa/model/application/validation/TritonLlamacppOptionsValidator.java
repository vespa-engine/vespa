// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import ai.vespa.embedding.config.GgufEmbedderConfig;
import com.yahoo.vespa.model.container.ContainerModelEvaluation;

import java.util.ArrayList;
import java.util.List;

/**
 * Fails the deployment if a GGUF embedder that runs in Triton has options that Triton's llama.cpp backend
 * does not accept. Without this check, the GGUF embedder would fail later, when it loads the model or embeds.
 * Validates the generated GgufEmbedderConfig, so that it covers embedders declared with type="gguf-embedder"
 * as well as those declared with the GgufEmbedder class.
 *
 * @author ingridhabrekke
 * @author glebashnik
 */
public class TritonLlamacppOptionsValidator implements Validator {

    // llama.cpp's micro-batch size when none is given.
    private static final int LLAMACPP_DEFAULT_UBATCH = 512;

    @Override
    public void validate(Validation.Context context) {
        context.model().getContainerClusters().forEach((__, cluster) -> {
            if ( ! cluster.usesTritonForLlamacpp()) return;
            for (var component : cluster.getAllComponents()) {
                if ( ! ContainerModelEvaluation.GGUF_EMBEDDER_CLASS.equals(component.getClassId().getName())) continue;
                var config = context.model().getConfig(GgufEmbedderConfig.class, component.getConfigId());
                for (var problem : unsupportedOptions(config))
                    context.illegal("GGUF embedder '" + component.getComponentId() + "' in cluster '" + cluster.getName() +
                                    "' runs in Triton, which does not support its options: " + problem);
            }
        });
    }

    /**
     * Returns why the llama.cpp backend of Triton would refuse the options, empty if it accepts them.
     * A GGUF embedder in Triton runs parallel model instances, each with contextSize / parallel of the context.
     * Triton's llama.cpp backend refuses an embedding model whose context per instance is smaller than the
     * micro-batch, or whose instance count does not divide the micro-batch.
     * On a host with several GPUs, Triton runs parallel instances per GPU, which this check does not cover.
     */
    static List<String> unsupportedOptions(GgufEmbedderConfig config) {
        var problems = new ArrayList<String>();
        if (config.poolingType() == GgufEmbedderConfig.PoolingType.Enum.NONE)
            problems.add("poolingType NONE: Triton returns only pooled embeddings. " +
                         "Set a pooling type, or leave it unspecified for a model that has one");
        int parallel = config.parallel();
        if (parallel < 1) {
            problems.add("parallel " + parallel + " is below 1");
            return problems;
        }
        int ubatch = config.physicalMaxBatchSize() > 0 ? config.physicalMaxBatchSize() : LLAMACPP_DEFAULT_UBATCH;
        var ubatchSource = config.physicalMaxBatchSize() > 0 ? "physicalMaxBatchSize " + ubatch
                                                             : "llama.cpp's default physicalMaxBatchSize " + ubatch;
        if (config.contextSize() > 0 && config.contextSize() / parallel < ubatch)
            problems.add("contextSize " + config.contextSize() + " gives " + config.contextSize() / parallel +
                         " per parallel sequence (parallel " + parallel + "), which is below " + ubatchSource +
                         ": each sequence must hold a whole input");
        if (ubatch % parallel != 0)
            problems.add("parallel " + parallel + " must divide " + ubatchSource);
        return problems;
    }

}
