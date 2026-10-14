// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import ai.vespa.triton.TritonOnnxRuntime;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Provides ONNX runtime environment with session management.
 *
 * @author bjorncs
 */
public interface OnnxRuntime {

    static OnnxRuntime testInstance() {
        var log = Logger.getLogger(OnnxRuntime.class.getName());
        if (Boolean.getBoolean("VESPA_USE_TRITON")) {
            log.info("Using Triton ONNX runtime for testing");
            return TritonOnnxRuntime.createTestInstance();
        } else {
            log.info("Using embedded ONNX runtime for testing");
            return EmbeddedOnnxRuntime.createTestInstance();
        }
    }

    static boolean isRuntimeAvailable() {
        return EmbeddedOnnxRuntime.isRuntimeAvailable();
    }

    static boolean isRuntimeAvailable(String modelPath) {
        return EmbeddedOnnxRuntime.isRuntimeAvailable(modelPath);
    }

    default OnnxEvaluator evaluatorOf(String modelPath) {
        return evaluatorOf(modelPath, OnnxEvaluatorOptions.createDefault());
    }
    OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options);

    /**
     * Returns an evaluator which pads sequence inputs as the padding options say, if this runtime batches
     * requests in a way padding helps with, see {@link SequencePadder}. Padding is only worthwhile with batching,
     * so a runtime that does not batch ignores the options, which is the default.
     */
    default OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options, Optional<SequencePadderOptions> padding) {
        return evaluatorOf(modelPath, options);
    }
}
