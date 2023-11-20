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
                               Optional<Integer> intraOpThreads, Optional<GpuDevice> gpuDevice) {

    public OnnxModelOptions(String executionMode, int interOpThreads, int intraOpThreads, GpuDevice gpuDevice) {
        this(Optional.of(executionMode), Optional.of(interOpThreads), Optional.of(intraOpThreads), Optional.of(gpuDevice));
    }

    public static OnnxModelOptions empty() {
        return new OnnxModelOptions(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public OnnxModelOptions withExecutionMode(String executionMode) {
        return new OnnxModelOptions(Optional.ofNullable(executionMode), interOpThreads, intraOpThreads, gpuDevice);
    }

    public OnnxModelOptions withInterOpThreads(Integer interOpThreads) {
        return new OnnxModelOptions(executionMode, Optional.ofNullable(interOpThreads), intraOpThreads, gpuDevice);
    }

    public OnnxModelOptions withIntraOpThreads(Integer intraOpThreads) {
        return new OnnxModelOptions(executionMode, interOpThreads, Optional.ofNullable(intraOpThreads), gpuDevice);
    }

    public OnnxModelOptions withGpuDevice(GpuDevice gpuDevice) {
        return new OnnxModelOptions(executionMode, interOpThreads, intraOpThreads, Optional.ofNullable(gpuDevice));
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
