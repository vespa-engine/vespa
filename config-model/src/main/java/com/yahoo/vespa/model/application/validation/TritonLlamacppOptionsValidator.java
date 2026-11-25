// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import ai.vespa.embedding.config.GgufEmbedderConfig;
import com.yahoo.vespa.model.container.ContainerModelEvaluation;

import java.util.ArrayList;
import java.util.List;

/**
 * Rejects GGUF embedder options that the llama.cpp backend of Triton refuses, in container clusters that run
 * their GGUF embedders in Triton, so that the deployment fails instead of the container.
 * Checks the config the components produce, so that it covers the gguf-embedder type and the GgufEmbedder class.
 *
 * @author ingridhabrekke
 * @author glebashnik
 */
public class TritonLlamacppOptionsValidator implements Validator {

    // llama-server's micro-batch size when none is given.
    private static final int LLAMA_SERVER_DEFAULT_UBATCH = 512;

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
     * The backend runs one llama-server slot per parallel sequence. A slot must hold a whole input,
     * and llama-server aborts unless the slots divide the micro-batch.
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
        int ubatch = config.physicalMaxBatchSize() > 0 ? config.physicalMaxBatchSize() : LLAMA_SERVER_DEFAULT_UBATCH;
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
