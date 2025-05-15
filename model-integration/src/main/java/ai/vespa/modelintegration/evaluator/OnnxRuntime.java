// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import ai.vespa.triton.TritonOnnxRuntime;

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
            return new TritonOnnxRuntime();
        } else {
            log.info("Using embedded ONNX runtime for testing");
            return new EmbeddedOnnxRuntime();
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
}
