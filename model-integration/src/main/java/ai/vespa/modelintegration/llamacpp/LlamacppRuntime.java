// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.llamacpp;

import java.util.Optional;

/**
 * Runs GGUF embedding models with llama.cpp, either in the container (EmbeddedLlamacppRuntime)
 * or in a Triton Inference Server sidecar (TritonLlamacppRuntime).
 * The counterpart of OnnxRuntime for GGUF models.
 * The config model adds exactly one of them to each container cluster, as it does for OnnxRuntime.
 *
 * @author ingridhabrekke
 */
public interface LlamacppRuntime {

    /** Returns an evaluator of the GGUF embedding model at the given path. The caller must close it. */
    LlamacppEvaluator evaluatorOf(String modelPath, LlamacppOptions options);

    /** Returns whether this runtime loads a multimodal projector, which embedding images needs. */
    default boolean loadsMmproj() { return false; }

    /**
     * Returns an evaluator of the GGUF embedding model with its multimodal projector, if any.
     * A runtime which does not load a projector returns an evaluator of text only.
     */
    default LlamacppEvaluator evaluatorOf(String modelPath, Optional<String> mmprojPath, LlamacppOptions options) {
        return evaluatorOf(modelPath, options);
    }

}
