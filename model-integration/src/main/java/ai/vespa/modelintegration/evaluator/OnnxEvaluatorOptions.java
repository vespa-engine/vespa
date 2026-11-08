// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Session options for ONNX Runtime evaluation.
 *
 * @author lesters
 * @author bjorncs
 * @author glebashnik
 */
public record OnnxEvaluatorOptions(
        ExecutionMode executionMode,
        int interOpThreads,
        int intraOpThreads,
        int gpuDeviceNumber,
        boolean gpuDeviceRequired,
        boolean optimizeModel,
        int batchingMaxSize,
        boolean firstDimensionIsBatch,
        Optional<Integer> numModelInstances,
        Optional<Path> modelConfigOverride,
        Optional<OnnxPadderOptions> padding,
        Optional<OnnxPoolingOptions> runtimePooling,
        int availableProcessors
) {

    public OnnxEvaluatorOptions {
        Objects.requireNonNull(executionMode, "executionMode cannot be null");
        Objects.requireNonNull(numModelInstances, "numModelInstances cannot be null");
        Objects.requireNonNull(modelConfigOverride, "modelConfigOverride cannot be null");
        Objects.requireNonNull(padding, "padding cannot be null");
        Objects.requireNonNull(runtimePooling, "runtimePooling cannot be null");
        if (!firstDimensionIsBatch && (padding.isPresent() || runtimePooling.isPresent()))
            throw new IllegalArgumentException("Padding and runtime pooling need a model whose first dimension is a batch");
    }

    public static OnnxEvaluatorOptions createDefault() {
        return new Builder().build();
    }

    public static Builder builder(OnnxEvaluatorConfig config) {
        return builder(config, Runtime.getRuntime().availableProcessors());
    }

    // availableProcessors is injected to simplify testing
    public static Builder builder(OnnxEvaluatorConfig config, int availableProcessors) {
        var concurrencyFactorType = OnnxEvaluatorOptions.ConcurrencyFactorType.fromString(
                config.concurrency().factorType().toString());

        var builder = new OnnxEvaluatorOptions.Builder(availableProcessors)
                .setExecutionMode(config.executionMode().toString())
                .setThreadsFromFactors(config.interOpThreads(), config.intraOpThreads())
                .setBatchingMaxSize(config.batching().maxSize())
                .setConcurrency(config.concurrency().factor(), concurrencyFactorType)
                .setModelConfigOverride(config.modelConfigOverride())
                .setPadding(OnnxPadderOptions.of(config))
                .setOptimizeModel(config.optimizeModel());

        if (config.gpuDevice() >= 0) {
            builder.setGpuDevice(config.gpuDevice());
        }

        // batching.maxDelayMillis is deprecated and intentionally ignored by both ONNX runtimes.

        return builder;
    }


    public enum ExecutionMode {
        SEQUENTIAL,
        PARALLEL;

        public static ExecutionMode fromString(String mode) {
            if ("parallel".equalsIgnoreCase(mode)) return PARALLEL;
            return SEQUENTIAL;
        }
    }

    public enum ConcurrencyFactorType {
        ABSOLUTE,
        RELATIVE;

        public static ConcurrencyFactorType fromString(String type) {
            if ("relative".equalsIgnoreCase(type)) return RELATIVE;
            return ABSOLUTE;
        }
    }

    public static class Builder {
        private ExecutionMode executionMode;
        private int interOpThreads;
        private int intraOpThreads;
        private int gpuDeviceNumber;
        private boolean gpuDeviceRequired;
        private boolean optimizeModel;
        private int batchingMaxSize;
        private boolean firstDimensionIsBatch;
        private Optional<Integer> numModelInstances;
        private Optional<Path> modelConfigOverride;
        private Optional<OnnxPadderOptions> padding;
        private Optional<OnnxPoolingOptions> runtimePooling;
        // Applied to the padding in build(), so that they do not depend on the order of the setters
        private Optional<String> paddingAttentionMask = Optional.empty();
        private OptionalInt paddingMaxTokens = OptionalInt.empty();

        // Used to calculate number of threads
        private int availableProcessors;

        public Builder() {
            this(Runtime.getRuntime().availableProcessors());
        }

        // availableProcessors is injected to simplify testing
        public Builder(int availableProcessors) {
            executionMode = ExecutionMode.SEQUENTIAL;
            interOpThreads = calculateThreads(-4, availableProcessors);
            intraOpThreads = calculateThreads(-4, availableProcessors);
            gpuDeviceNumber = -1;
            gpuDeviceRequired = false;
            optimizeModel = false;
            batchingMaxSize = 1;
            // A dynamic first dimension is not always a batch, e.g. in a pairwise similarity model.
            firstDimensionIsBatch = false;
            numModelInstances = Optional.empty();
            modelConfigOverride = Optional.empty();
            padding = Optional.empty();
            runtimePooling = Optional.empty();

            this.availableProcessors = availableProcessors;
        }
        
        public Builder(OnnxEvaluatorOptions options) {
            this.executionMode = options.executionMode();
            this.interOpThreads = options.interOpThreads();
            this.intraOpThreads = options.intraOpThreads();
            this.gpuDeviceNumber = options.gpuDeviceNumber();
            this.gpuDeviceRequired = options.gpuDeviceRequired();
            this.optimizeModel = options.optimizeModel();
            this.batchingMaxSize = options.batchingMaxSize();
            this.firstDimensionIsBatch = options.firstDimensionIsBatch();
            this.numModelInstances = options.numModelInstances();
            this.modelConfigOverride = options.modelConfigOverride;
            this.padding = options.padding();
            this.runtimePooling = options.runtimePooling();
            this.availableProcessors = options.availableProcessors();
        }

        public Builder setExecutionMode(String mode) {
            return setExecutionMode(ExecutionMode.fromString(mode));
        }

        public Builder setExecutionMode(ExecutionMode mode) {
            executionMode = mode;
            return this;
        }

        public Builder setInterOpThreads(int threads) {
            if (threads >= 0) interOpThreads = threads;
            return this;
        }

        public Builder setIntraOpThreads(int threads) {
            if (threads >= 0) intraOpThreads = threads;
            return this;
        }

        /**
         * Sets the number of threads for inter-op and intra-op ONNX parallel execution based on the provided factors.
         * Positive number is interpreted as an absolute number of threads.
         * A negative number is interpreted as an inverse scaling factor <code>threads=CPU/-n</code>
         */
        public Builder setThreadsFromFactors(int interOpThreadsFactor, int intraOpThreadsFactor) {
            interOpThreads = calculateThreads(interOpThreadsFactor, availableProcessors);
            intraOpThreads = calculateThreads(intraOpThreadsFactor, availableProcessors);
            return this;
        }

        public Builder setGpuDevice(int deviceNumber, boolean required) {
            this.gpuDeviceNumber = deviceNumber;
            this.gpuDeviceRequired = required;
            return this;
        }

        public Builder setGpuDevice(int deviceNumber) {
            gpuDeviceNumber = deviceNumber;
            return this;
        }

        public Builder setOptimizeModel(boolean optimizeModel) {
            this.optimizeModel = optimizeModel;
            return this;
        }

        public Builder setBatchingMaxSize(int maxSize) {
            this.batchingMaxSize = maxSize;
            return this;
        }

        /**
         * Sets whether the first dimension of the model's inputs and outputs is a batch:
         * the model computes each row on its own, so a runtime may combine rows from several requests.
         * False by default.
         */
        public Builder setFirstDimensionIsBatch(boolean firstDimensionIsBatch) {
            this.firstDimensionIsBatch = firstDimensionIsBatch;
            return this;
        }
        
        /**
         * Sets the number of model instances from a concurrency factor.
         * A non-positive factor leaves the number of instances unset, so the runtime derives a default.
         * Application package schemas only allow positive explicit factors.
         * Non-positive values can occur through the config default or a raw config override.
         */
        public Builder setConcurrency(double concurrencyFactor, ConcurrencyFactorType concurrencyFactorType) {
            this.numModelInstances = concurrencyFactor > 0
                    ? Optional.of(calculateNumModelInstances(concurrencyFactor, concurrencyFactorType))
                    : Optional.empty();
            return this;
        }

        private int calculateNumModelInstances(double concurrencyFactor, ConcurrencyFactorType type) {
            if (type == ConcurrencyFactorType.ABSOLUTE) {
                return Math.max(1, Math.toIntExact(Math.round(concurrencyFactor)));
            } else if (type == ConcurrencyFactorType.RELATIVE) {
                return Math.max(1, Math.toIntExact(Math.round(concurrencyFactor * availableProcessors)));
            } else {
                throw new IllegalArgumentException("Unhandled concurrency factor type: " + type.toString());
            }
        }

        public Builder setNumModelInstances(int numModelInstances) {
            if (numModelInstances <= 0) {
                throw new IllegalArgumentException("Number of model instances must be positive, got " + numModelInstances);
            }
            this.numModelInstances = Optional.of(numModelInstances);
            return this;
        }

        public Builder setModelConfigOverride(Optional<Path> configFile) {
            this.modelConfigOverride = configFile;
            return this;
        }

        public Builder setPadding(Optional<OnnxPadderOptions> padding) {
            this.padding = padding;
            return this;
        }

        /** Sets the attention mask input that padding uses, if padding is configured */
        public Builder setPaddingAttentionMask(String name) {
            this.paddingAttentionMask = Optional.of(name);
            return this;
        }

        /** Limits padding to at most the given max tokens, the longest input, if padding is configured */
        public Builder setPaddingMaxTokens(int maxTokens) {
            this.paddingMaxTokens = OptionalInt.of(maxTokens);
            return this;
        }

        public Builder setRuntimePooling(Optional<OnnxPoolingOptions> runtimePooling) {
            this.runtimePooling = runtimePooling;
            return this;
        }

        public OnnxEvaluatorOptions build() {
            var refinedPadding = padding
                    .map(p -> paddingAttentionMask.map(p::withAttentionMask).orElse(p))
                    .map(p -> paddingMaxTokens.isPresent() ? p.withMaxTokens(paddingMaxTokens.getAsInt()) : p);
            return new OnnxEvaluatorOptions(
                    executionMode,
                    interOpThreads,
                    intraOpThreads,
                    gpuDeviceNumber,
                    gpuDeviceRequired,
                    optimizeModel,
                    batchingMaxSize,
                    firstDimensionIsBatch,
                    numModelInstances,
                    modelConfigOverride,
                    refinedPadding,
                    runtimePooling,
                    availableProcessors
            );
        }
    }

    /**
     * Returns the number of threads for the given factor.
     * A non-negative factor is an absolute number of threads.
     * A negative factor divides the CPU cores by its magnitude, rounding up, with at least one thread.
     */
    public static int calculateThreads(int threadsFactor, int availableProcessors) {
        if (threadsFactor >= 0) {
            return threadsFactor;
        }

        return Math.max(1, (int) Math.ceil(-1d * availableProcessors / threadsFactor));
    }

    public boolean requestingGpu() {
        return gpuDeviceNumber > -1;
    }

    public boolean isParallel() {
        return executionMode == ExecutionMode.PARALLEL;
    }

    /** Returns the inter-op threads ONNX Runtime uses. It uses one thread in sequential execution mode. */
    public int effectiveInterOpThreads() {
        return isParallel() ? interOpThreads : 1;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }
}
