// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import java.util.Objects;

/**
 * Session options for ONNX Runtime evaluation
 *
 * @author lesters
 * @author bjorncs
 */
public record OnnxEvaluatorOptions(
        ExecutionMode executionMode,
        int interOpThreads,
        int intraOpThreads,
        int gpuDeviceNumber,
        boolean gpuDeviceRequired) {


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

    public static class Builder {
        private ExecutionMode executionMode;
        private int interOpThreads;
        private int intraOpThreads;
        private int gpuDeviceNumber;
        private boolean gpuDeviceRequired;

        public Builder() {
            executionMode = ExecutionMode.SEQUENTIAL;
            int quarterVcpu = Math.max(1, (int) Math.ceil(Runtime.getRuntime().availableProcessors() / 4d));
            interOpThreads = quarterVcpu;
            intraOpThreads = quarterVcpu;
            gpuDeviceNumber = -1;
            gpuDeviceRequired = false;
        }

        public Builder(OnnxEvaluatorOptions options) {
            this.executionMode = options.executionMode();
            this.interOpThreads = options.interOpThreads();
            this.intraOpThreads = options.intraOpThreads();
            this.gpuDeviceNumber = options.gpuDeviceNumber();
            this.gpuDeviceRequired = options.gpuDeviceRequired();
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

        public OnnxEvaluatorOptions build() {
            return new OnnxEvaluatorOptions(
                    executionMode,
                    interOpThreads,
                    intraOpThreads,
                    gpuDeviceNumber,
                    gpuDeviceRequired);
        }
    }

    public boolean requestingGpu() {
        return gpuDeviceNumber > -1;
    }
}
