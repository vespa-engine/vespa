// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.llamacpp;

/**
 * Runs GGUF embedding models with llama.cpp, either through its Java bindings (EmbeddedLlamacppRuntime)
 * or in Triton Inference Server (TritonLlamacppRuntime).
 * The counterpart of OnnxRuntime for GGUF models.
 *
 * @author ingridhabrekke
 * @author glebashnik
 */
public interface LlamacppRuntime {

    /** Returns an evaluator of the GGUF embedding model at the given path. The caller must close it. */
    LlamacppEvaluator evaluatorOf(String modelPath, LlamacppOptions options);

}
