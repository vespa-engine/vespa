// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.FileReference;

import java.time.Duration;
import java.util.Optional;

/**
 * Onnx model options that are relevant when deciding if an Onnx model needs to be reloaded. If any of the
 * values in this class change, reload is needed.
 *
 * @author hmusum
 * @author glebashnik
 */
public record OnnxModelOptions(
        Optional<String> executionMode,
        Optional<Integer> interOpThreads,
        Optional<Integer> intraOpThreads,
        Optional<GpuDevice> gpuDevice,
        Optional<Integer> batchingMaxSize,
        Optional<Duration> batchingMaxDelay,
        Optional<String> concurrencyFactorType,
        Optional<Double> concurrencyFactor,
        Optional<FileReference> modelConfigOverride) {
    public OnnxModelOptions(
            Optional<String> executionMode,
            Optional<Integer> interOpThreads,
            Optional<Integer> intraOpThreads,
            Optional<GpuDevice> gpuDevice) {
        this(new Builder()
                .executionMode(executionMode.orElse(null))
                .interOpThreads(interOpThreads.orElse(null))
                .intraOpThreads(intraOpThreads.orElse(null))
                .gpuDevice(gpuDevice.orElse(null)));
    }

    public OnnxModelOptions(String executionMode, int interOpThreads, int intraOpThreads, GpuDevice gpuDevice) {
        this(new Builder()
                .executionMode(executionMode)
                .interOpThreads(interOpThreads)
                .intraOpThreads(intraOpThreads)
                .gpuDevice(gpuDevice));
    }

    private OnnxModelOptions(Builder builder) {
        this(
                builder.executionMode,
                builder.interOpThreads,
                builder.intraOpThreads,
                builder.gpuDevice,
                builder.batchingMaxSize,
                builder.batchingMaxDelay,
                builder.concurrencyFactorType,
                builder.concurrencyFactor,
                builder.modelConfigOverride);
    }

    public static OnnxModelOptions empty() {
        return new Builder().build();
    }

    public OnnxModelOptions withExecutionMode(String executionMode) {
        return toBuilder().executionMode(executionMode).build();
    }

    public OnnxModelOptions withInterOpThreads(Integer interOpThreads) {
        return toBuilder().interOpThreads(interOpThreads).build();
    }

    public OnnxModelOptions withIntraOpThreads(Integer intraOpThreads) {
        return toBuilder().intraOpThreads(intraOpThreads).build();
    }

    public OnnxModelOptions withGpuDevice(GpuDevice gpuDevice) {
        return toBuilder().gpuDevice(gpuDevice).build();
    }

    public OnnxModelOptions withBatchingMaxSize(Integer batchingMaxSize) {
        return toBuilder().batchingMaxSize(batchingMaxSize).build();
    }

    public OnnxModelOptions withBatchingMaxDelay(Duration batchingMaxDelay) {
        return toBuilder().batchingMaxDelay(batchingMaxDelay).build();
    }

    public OnnxModelOptions withConcurrencyType(String concurrencyType) {
        return toBuilder().concurrencyFactorType(concurrencyType).build();
    }

    public OnnxModelOptions withConcurrencyFactorType(Double concurrencyFactor) {
        return toBuilder().concurrencyFactor(concurrencyFactor).build();
    }

    public OnnxModelOptions withModelConfigOverride(FileReference modelConfigOverride) {
        return toBuilder().modelConfigOverride(modelConfigOverride).build();
    }

    private Builder toBuilder() {
        return new Builder(this);
    }

    private static class Builder {
        private Optional<String> executionMode = Optional.empty();
        private Optional<Integer> interOpThreads = Optional.empty();
        private Optional<Integer> intraOpThreads = Optional.empty();
        private Optional<GpuDevice> gpuDevice = Optional.empty();
        private Optional<Integer> batchingMaxSize = Optional.empty();
        private Optional<Duration> batchingMaxDelay = Optional.empty();
        private Optional<String> concurrencyFactorType = Optional.empty();
        private Optional<Double> concurrencyFactor = Optional.empty();
        private Optional<FileReference> modelConfigOverride = Optional.empty();

        Builder() {}

        Builder(OnnxModelOptions options) {
            this.executionMode = options.executionMode;
            this.interOpThreads = options.interOpThreads;
            this.intraOpThreads = options.intraOpThreads;
            this.gpuDevice = options.gpuDevice;
            this.batchingMaxSize = options.batchingMaxSize;
            this.batchingMaxDelay = options.batchingMaxDelay;
            this.concurrencyFactorType = options.concurrencyFactorType;
            this.concurrencyFactor = options.concurrencyFactor;
            this.modelConfigOverride = options.modelConfigOverride;
        }

        Builder executionMode(String executionMode) {
            this.executionMode = Optional.ofNullable(executionMode);
            return this;
        }

        Builder interOpThreads(Integer interOpThreads) {
            this.interOpThreads = Optional.ofNullable(interOpThreads);
            return this;
        }

        Builder intraOpThreads(Integer intraOpThreads) {
            this.intraOpThreads = Optional.ofNullable(intraOpThreads);
            return this;
        }

        Builder gpuDevice(GpuDevice gpuDevice) {
            this.gpuDevice = Optional.ofNullable(gpuDevice);
            return this;
        }

        Builder batchingMaxSize(Integer batchingMaxSize) {
            this.batchingMaxSize = Optional.ofNullable(batchingMaxSize);
            return this;
        }

        Builder batchingMaxDelay(Duration batchingMaxDelay) {
            this.batchingMaxDelay = Optional.ofNullable(batchingMaxDelay);
            return this;
        }

        Builder concurrencyFactorType(String concurrencyFactorType) {
            this.concurrencyFactorType = Optional.ofNullable(concurrencyFactorType);
            return this;
        }

        Builder concurrencyFactor(Double concurrencyFactor) {
            this.concurrencyFactor = Optional.ofNullable(concurrencyFactor);
            return this;
        }

        Builder modelConfigOverride(FileReference modelConfigOverride) {
            this.modelConfigOverride = Optional.ofNullable(modelConfigOverride);
            return this;
        }

        OnnxModelOptions build() {
            return new OnnxModelOptions(this);
        }
    }

    public record GpuDevice(int deviceNumber, boolean required) {
        public GpuDevice {
            if (deviceNumber < 0)
                throw new IllegalArgumentException("deviceNumber cannot be negative, got " + deviceNumber);
        }

        public GpuDevice(int deviceNumber) {
            this(deviceNumber, false);
        }
    }
}
