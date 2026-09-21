// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;
import net.jpountz.xxhash.XXHashFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Session options for ONNX Runtime evaluation
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
        Optional<Integer> numModelInstances,
        Optional<Path> modelConfigOverride,
        int availableProcessors
) {

    // Unlike hashCode, this hash doesn't change between runs
    public long calculateHash() {
        var bytes = toString().getBytes(StandardCharsets.UTF_8);
        return XXHashFactory.fastestInstance().hash64().hash(bytes, 0, bytes.length, 0);
    }

    public OnnxEvaluatorOptions {
        Objects.requireNonNull(executionMode, "executionMode cannot be null");
    }

    public static OnnxEvaluatorOptions createDefault() {
        return new Builder().build();
    }

    public static OnnxEvaluatorOptions of(OnnxEvaluatorConfig config) {
        return of(config, Runtime.getRuntime().availableProcessors());
    }

    // availableProcessors is injected to simplify testing
    public static OnnxEvaluatorOptions of(OnnxEvaluatorConfig config, int availableProcessors) {
        var concurrencyFactorType = OnnxEvaluatorOptions.ConcurrencyFactorType.fromString(
                config.concurrency().factorType().toString());

        var builder = new OnnxEvaluatorOptions.Builder(availableProcessors)
                .setExecutionMode(config.executionMode().toString())
                .setThreadsFromFactors(config.interOpThreads(), config.intraOpThreads())
                .setBatchingMaxSize(config.batching().maxSize())
                .setConcurrency(config.concurrency().factor(), concurrencyFactorType)
                .setModelConfigOverride(config.modelConfigOverride())
                .setOptimizeModel(config.optimizeModel());

        if (config.gpuDevice() >= 0) {
            builder.setGpuDevice(config.gpuDevice());
        }

        // batching.maxDelayMillis is deprecated and intentionally ignored by both ONNX runtimes.

        return builder.build();
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
        private Optional<Integer> numModelInstances;
        private Optional<Path> modelConfigOverride;

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
            numModelInstances = Optional.empty();
            modelConfigOverride = Optional.empty();
            
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
            this.numModelInstances = options.numModelInstances();
            this.modelConfigOverride = options.modelConfigOverride;
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
            this.numModelInstances = Optional.of(numModelInstances);
            return this;
        }

        public Builder setModelConfigOverride(Optional<Path> configFile) {
            this.modelConfigOverride = configFile;
            return this;
        }

        public OnnxEvaluatorOptions build() {
            return new OnnxEvaluatorOptions(
                    executionMode,
                    interOpThreads,
                    intraOpThreads,
                    gpuDeviceNumber,
                    gpuDeviceRequired,
                    optimizeModel,
                    batchingMaxSize,
                    numModelInstances,
                    modelConfigOverride,
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

    public OnnxEvaluatorOptions withNumModelInstances(int numModelInstances) {
        return new Builder(this).setNumModelInstances(numModelInstances).build();
    }
}
