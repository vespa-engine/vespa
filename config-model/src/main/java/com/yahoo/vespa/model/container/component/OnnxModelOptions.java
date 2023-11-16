// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.ModelReference;

/**
 * @author hmusum
 */
public record OnnxModelOptions(ModelReference modelRef, ModelReference vocabRef, Integer maxTokens,
                               String transformerInputIds, String transformerAttentionMask, String transformerTokenTypeIds,
                               String transformerOutput, Boolean normalize, String executionMode, Integer interOpThreads,
                               Integer intraOpThreads, GpuDevice gpuDevice, String poolingStrategy,
                               Integer transformerStartSequenceToken, Integer transformerEndSequenceToken,
                               Integer maxQueryTokens, Integer maxDocumentTokens, Integer transformerMaskToken) {


    public OnnxModelOptions(ModelReference modelRef, ModelReference vocabRef, Integer maxTokens,
                     String transformerInputIds, String transformerAttentionMask, String transformerTokenTypeIds,
                     String transformerOutput, Boolean normalize, String executionMode, Integer interOpThreads,
                     Integer intraOpThreads, GpuDevice gpuDevice, String poolingStrategy) {
        this(modelRef, vocabRef, maxTokens, transformerInputIds, transformerAttentionMask, transformerTokenTypeIds,
             transformerOutput, normalize, executionMode, interOpThreads, intraOpThreads, gpuDevice, poolingStrategy,
             null, null, null, null, null);
    }

    public OnnxModelOptions(ModelReference modelRef, ModelReference vocabRef, Integer maxTokens,
                            String transformerInputIds, String transformerAttentionMask, String transformerTokenTypeIds,
                            String transformerOutput, Boolean normalize, String executionMode, Integer interOpThreads,
                            Integer intraOpThreads, GpuDevice gpuDevice, String poolingStrategy,
                            Integer transformerStartSequenceToken, Integer transformerEndSequenceToken) {
        this(modelRef, vocabRef, maxTokens, transformerInputIds, transformerAttentionMask, transformerTokenTypeIds,
             transformerOutput, normalize, executionMode, interOpThreads, intraOpThreads, gpuDevice, poolingStrategy,
             transformerStartSequenceToken, transformerEndSequenceToken, null, null, null);
    }

    public static OnnxModelOptions empty() {
        return new OnnxModelOptions(null, null, null, null, null, null, null, null, null, null, null, null, null,
                                    null, null, null, null, null);
    }

    public OnnxModelOptions withExecutionMode(String executionMode) {
        return new OnnxModelOptions(modelRef, vocabRef, maxTokens, transformerInputIds, transformerAttentionMask,
                                    transformerTokenTypeIds, transformerOutput, normalize, executionMode,
                                    interOpThreads, intraOpThreads, gpuDevice, poolingStrategy,
                                    transformerStartSequenceToken, transformerEndSequenceToken,
                                    maxQueryTokens, maxDocumentTokens, transformerMaskToken);
    }

    public OnnxModelOptions withInteropThreads(Integer interopThreads) {
        return new OnnxModelOptions(modelRef, vocabRef, maxTokens, transformerInputIds, transformerAttentionMask,
                                    transformerTokenTypeIds, transformerOutput, normalize, executionMode,
                                    interopThreads, intraOpThreads, gpuDevice, poolingStrategy,
                                    transformerStartSequenceToken, transformerEndSequenceToken,
                                    maxQueryTokens, maxDocumentTokens, transformerMaskToken);
    }

    public OnnxModelOptions withIntraopThreads(Integer intraopThreads) {
        return new OnnxModelOptions(modelRef, vocabRef, maxTokens, transformerInputIds, transformerAttentionMask,
                                    transformerTokenTypeIds, transformerOutput, normalize, executionMode,
                                    interOpThreads, intraopThreads, gpuDevice, poolingStrategy,
                                    transformerStartSequenceToken, transformerEndSequenceToken,
                                    maxQueryTokens, maxDocumentTokens, transformerMaskToken);
    }


    public OnnxModelOptions withGpuDevice(GpuDevice gpuDevice) {
        return new OnnxModelOptions(modelRef, vocabRef, maxTokens, transformerInputIds, transformerAttentionMask,
                                    transformerTokenTypeIds, transformerOutput, normalize, executionMode,
                                    interOpThreads, intraOpThreads, gpuDevice, poolingStrategy,
                                    transformerStartSequenceToken, transformerEndSequenceToken,
                                    maxQueryTokens, maxDocumentTokens, transformerMaskToken);
    }

    public record GpuDevice(int deviceNumber, boolean required) {
        public GpuDevice {
            if (deviceNumber < 0) throw new IllegalArgumentException("deviceNumber cannot be negative, got " + deviceNumber);
        }

        public GpuDevice(int deviceNumber) {
            this(deviceNumber, false);
        }
    }

}
