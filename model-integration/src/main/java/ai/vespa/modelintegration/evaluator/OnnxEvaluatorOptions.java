// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

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
        int batchingMaxSize,
        int batchingMaxDelayMillis,
        int numModelInstances,
        Optional<Path> modelConfigOverride
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
        private int batchingMaxSize;
        private int batchingMaxDelayMillis;
        private int numModelInstances;
        private Optional<Path> modelConfigOverride;
        private int availableProcessors;


        public Builder() {
            this(Runtime.getRuntime().availableProcessors());
        }

        // availableProcessors is injected for testing, 
        // Mockito can't mock Runtime.getRuntime().availableProcessors() because it is native.
        public Builder(int availableProcessors) {
            executionMode = ExecutionMode.SEQUENTIAL;
            this.availableProcessors = availableProcessors;
            int quarterVcpu = Math.max(1, (int) Math.ceil(availableProcessors / 4d));
            interOpThreads = quarterVcpu;
            intraOpThreads = quarterVcpu;
            gpuDeviceNumber = -1;
            gpuDeviceRequired = false;
            batchingMaxSize = 1;
            batchingMaxDelayMillis = 1;
            numModelInstances = 1;
            modelConfigOverride = Optional.empty();
        }

        public Builder(OnnxEvaluatorOptions options) {
            this.executionMode = options.executionMode();
            this.interOpThreads = options.interOpThreads();
            this.intraOpThreads = options.intraOpThreads();
            this.gpuDeviceNumber = options.gpuDeviceNumber();
            this.gpuDeviceRequired = options.gpuDeviceRequired();
            this.batchingMaxSize = options.batchingMaxSize();
            this.batchingMaxDelayMillis = options.batchingMaxDelayMillis();
            this.numModelInstances = options.numModelInstances();
            this.modelConfigOverride = options.modelConfigOverride;
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
         * Sets the number of threads for inter and intra op execution.
         * A negative number is interpreted as an inverse scaling factor <code>threads=CPU/-n</code>
         */
        public Builder setThreads(int interOp, int intraOp) {
            interOpThreads = calculateThreads(interOp);
            intraOpThreads = calculateThreads(intraOp);
            return this;
        }

        private static int calculateThreads(int t) {
            if (t >= 0) return t;
            return Math.max(1, (int) Math.ceil(-1d * Runtime.getRuntime().availableProcessors() / t));
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

        public Builder setBatching(int maxSize, int batchMaxDelayMillis) {
            this.batchingMaxSize = maxSize;
            this.batchingMaxDelayMillis = batchMaxDelayMillis;
            return this;
        }

        public Builder setConcurrency(double concurrencyFactor, ConcurrencyFactorType concurrencyFactorType) {
            this.numModelInstances = calculateNumModelInstances(concurrencyFactor, concurrencyFactorType);
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
                    batchingMaxSize,
                    batchingMaxDelayMillis,
                    numModelInstances,
                    modelConfigOverride
            );
        }
    }

    public boolean requestingGpu() {
        return gpuDeviceNumber > -1;
    }
}
