// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.ModelReference;

import java.util.Optional;

/**
 * @author hmusum
 */
public record OnnxModelOptions(Optional<ModelReference> modelRef, Optional<ModelReference> vocabRef,
                               Optional<Integer> maxTokens, Optional<String> transformerInputIds,
                               Optional<String> transformerAttentionMask, Optional<String> transformerTokenTypeIds,
                               Optional<String> transformerOutput, Optional<Boolean> normalize,
                               Optional<String> executionMode, Optional<Integer> interOpThreads,
                               Optional<Integer> intraOpThreads, Optional<GpuDevice> gpuDevice,
                               Optional<String> poolingStrategy, Optional<Integer> transformerStartSequenceToken,
                               Optional<Integer> transformerEndSequenceToken, Optional<Integer> maxQueryTokens,
                               Optional<Integer> maxDocumentTokens, Optional<Integer> transformerMaskToken) {


    public OnnxModelOptions(Optional<ModelReference> modelRef, Optional<ModelReference> vocabRef,
                            Optional<Integer> maxTokens, Optional<String> transformerInputIds,
                            Optional<String> transformerAttentionMask, Optional<String> transformerTokenTypeIds,
                            Optional<String> transformerOutput, Optional<Boolean> normalize,
                            Optional<String> executionMode, Optional<Integer> interOpThreads,
                            Optional<Integer> intraOpThreads, Optional<GpuDevice> gpuDevice,
                            Optional<String> poolingStrategy) {
        this(modelRef, vocabRef, maxTokens, transformerInputIds, transformerAttentionMask, transformerTokenTypeIds,
             transformerOutput, normalize, executionMode, interOpThreads, intraOpThreads, gpuDevice, poolingStrategy,
             Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public OnnxModelOptions(Optional<ModelReference> modelRef, Optional<ModelReference> vocabRef,
                            Optional<Integer> maxTokens, Optional<String> transformerInputIds,
                            Optional<String> transformerAttentionMask, Optional<String> transformerTokenTypeIds,
                            Optional<String> transformerOutput, Optional<Boolean> normalize,
                            Optional<String> executionMode, Optional<Integer> interOpThreads,
                            Optional<Integer> intraOpThreads, Optional<GpuDevice> gpuDevice,
                            Optional<String> poolingStrategy, Optional<Integer> transformerStartSequenceToken,
                            Optional<Integer> transformerEndSequenceToken) {
        this(modelRef, vocabRef, maxTokens, transformerInputIds, transformerAttentionMask, transformerTokenTypeIds,
             transformerOutput, normalize, executionMode, interOpThreads, intraOpThreads, gpuDevice, poolingStrategy,
             transformerStartSequenceToken, transformerEndSequenceToken, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static OnnxModelOptions empty() {
        return new OnnxModelOptions(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public OnnxModelOptions withExecutionMode(String executionMode) {
        return new OnnxModelOptions(modelRef, vocabRef, maxTokens, transformerInputIds, transformerAttentionMask,
                                    transformerTokenTypeIds, transformerOutput, normalize, Optional.ofNullable(executionMode),
                                    interOpThreads, intraOpThreads, gpuDevice, poolingStrategy,
                                    transformerStartSequenceToken, transformerEndSequenceToken,
                                    maxQueryTokens, maxDocumentTokens, transformerMaskToken);
    }

    public OnnxModelOptions withInteropThreads(Integer interopThreads) {
        return new OnnxModelOptions(modelRef, vocabRef, maxTokens, transformerInputIds, transformerAttentionMask,
                                    transformerTokenTypeIds, transformerOutput, normalize, executionMode,
                                    Optional.ofNullable(interopThreads), intraOpThreads, gpuDevice, poolingStrategy,
                                    transformerStartSequenceToken, transformerEndSequenceToken,
                                    maxQueryTokens, maxDocumentTokens, transformerMaskToken);
    }

    public OnnxModelOptions withIntraopThreads(Integer intraopThreads) {
        return new OnnxModelOptions(modelRef, vocabRef, maxTokens, transformerInputIds, transformerAttentionMask,
                                    transformerTokenTypeIds, transformerOutput, normalize, executionMode,
                                    interOpThreads, Optional.ofNullable(intraopThreads), gpuDevice, poolingStrategy,
                                    transformerStartSequenceToken, transformerEndSequenceToken,
                                    maxQueryTokens, maxDocumentTokens, transformerMaskToken);
    }


    public OnnxModelOptions withGpuDevice(GpuDevice gpuDevice) {
        return new OnnxModelOptions(modelRef, vocabRef, maxTokens, transformerInputIds, transformerAttentionMask,
                                    transformerTokenTypeIds, transformerOutput, normalize, executionMode,
                                    interOpThreads, intraOpThreads, Optional.ofNullable(gpuDevice), poolingStrategy,
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
