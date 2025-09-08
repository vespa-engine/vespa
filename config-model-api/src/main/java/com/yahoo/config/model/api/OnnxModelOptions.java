// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.util.Optional;

/**
 * Onnx model options that are relevant when deciding if an Onnx model needs to be reloaded. If any of the
 * values in this class change, reload is needed.
 *
 * @author hmusum
 */
public record OnnxModelOptions(Optional<String> executionMode, Optional<Integer> interOpThreads,
                               Optional<Integer> intraOpThreads,
                               DimensionResolving dimensionResolving,
                               Optional<GpuDevice> gpuDevice) {

    public OnnxModelOptions(String executionMode, int interOpThreads, int intraOpThreads, GpuDevice gpuDevice) {
        this(Optional.of(executionMode), Optional.of(interOpThreads), Optional.of(intraOpThreads), oldDR, Optional.of(gpuDevice));
    }

    public static OnnxModelOptions empty() {
        return new OnnxModelOptions(Optional.empty(), Optional.empty(), Optional.empty(), oldDR, Optional.empty());
    }

    public OnnxModelOptions withExecutionMode(String executionMode) {
        return new OnnxModelOptions(Optional.ofNullable(executionMode), interOpThreads, intraOpThreads, dimensionResolving, gpuDevice);
    }

    public OnnxModelOptions withDimensionResolving(String value) {
        return new OnnxModelOptions(executionMode, interOpThreads, intraOpThreads, DimensionResolving.valueOf(value), gpuDevice);
    }

    public OnnxModelOptions withInterOpThreads(Integer interOpThreads) {
        return new OnnxModelOptions(executionMode, Optional.ofNullable(interOpThreads), intraOpThreads, dimensionResolving, gpuDevice);
    }

    public OnnxModelOptions withIntraOpThreads(Integer intraOpThreads) {
        return new OnnxModelOptions(executionMode, interOpThreads, Optional.ofNullable(intraOpThreads), dimensionResolving, gpuDevice);
    }

    public OnnxModelOptions withGpuDevice(GpuDevice gpuDevice) {
        return new OnnxModelOptions(executionMode, interOpThreads, intraOpThreads, dimensionResolving, Optional.ofNullable(gpuDevice));
    }

    public record GpuDevice(int deviceNumber, boolean required) {
        public GpuDevice {
            if (deviceNumber < 0) throw new IllegalArgumentException("deviceNumber cannot be negative, got " + deviceNumber);
        }

        public GpuDevice(int deviceNumber) {
            this(deviceNumber, false);
        }
    }

    public enum DimensionResolving {
        D_NUMBERS,
        DETECT_NAMES,
        DETECT_NAMES_AND_TYPES;
    }
    private static DimensionResolving oldDR = DimensionResolving.D_NUMBERS;
}
