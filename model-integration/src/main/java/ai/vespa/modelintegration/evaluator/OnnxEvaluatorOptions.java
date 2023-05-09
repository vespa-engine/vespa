// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.util.Objects;

import static ai.onnxruntime.OrtSession.SessionOptions.ExecutionMode.PARALLEL;
import static ai.onnxruntime.OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL;

/**
 * Session options for ONNX Runtime evaluation
 *
 * @author lesters
 */
public class OnnxEvaluatorOptions {

    private OrtSession.SessionOptions.OptLevel optimizationLevel;
    private OrtSession.SessionOptions.ExecutionMode executionMode;
    private int interOpThreads;
    private int intraOpThreads;
    private int gpuDeviceNumber;
    private boolean gpuDeviceRequired;

    public OnnxEvaluatorOptions() {
        // Defaults:
        optimizationLevel = OrtSession.SessionOptions.OptLevel.ALL_OPT;
        executionMode = SEQUENTIAL;
        int quarterVcpu = Math.max(1, (int) Math.ceil(Runtime.getRuntime().availableProcessors() / 4d));
        interOpThreads = quarterVcpu;
        intraOpThreads = quarterVcpu;
        gpuDeviceNumber = -1;
        gpuDeviceRequired = false;
    }

    public OrtSession.SessionOptions getOptions(boolean loadCuda) throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(optimizationLevel);
        options.setExecutionMode(executionMode);
        options.setInterOpNumThreads(executionMode == PARALLEL ? interOpThreads : 1);
        options.setIntraOpNumThreads(intraOpThreads);
        if (loadCuda) {
            options.addCUDA(gpuDeviceNumber);
        }
        return options;
    }

    public void setExecutionMode(String mode) {
        if ("parallel".equalsIgnoreCase(mode)) {
            executionMode = OrtSession.SessionOptions.ExecutionMode.PARALLEL;
        } else if ("sequential".equalsIgnoreCase(mode)) {
            executionMode = SEQUENTIAL;
        }
    }

    public void setInterOpThreads(int threads) {
        if (threads >= 0) {
            interOpThreads = threads;
        }
    }

    public void setIntraOpThreads(int threads) {
        if (threads >= 0) {
            intraOpThreads = threads;
        }
    }

    /**
     * Sets the number of threads for inter and intra op execution.
     * A negative number is interpreted as an inverse scaling factor <code>threads=CPU/-n</code>
     */
    public void setThreads(int interOp, int intraOp) {
        interOpThreads = calculateThreads(interOp);
        intraOpThreads = calculateThreads(intraOp);
    }

    private static int calculateThreads(int t) {
        if (t >= 0) return t;
        return Math.max(1, (int) Math.ceil(-1d * Runtime.getRuntime().availableProcessors() / t));
    }

    public void setGpuDevice(int deviceNumber, boolean required) {
        this.gpuDeviceNumber = deviceNumber;
        this.gpuDeviceRequired = required;
    }

    public void setGpuDevice(int deviceNumber) { gpuDeviceNumber = deviceNumber; }

    public boolean requestingGpu() {
        return gpuDeviceNumber > -1;
    }

    public boolean gpuDeviceRequired() {
        return gpuDeviceRequired;
    }

    public int gpuDeviceNumber() { return gpuDeviceNumber; }

    public OnnxEvaluatorOptions copy() {
        var copy = new OnnxEvaluatorOptions();
        copy.gpuDeviceNumber = gpuDeviceNumber;
        copy.gpuDeviceRequired = gpuDeviceRequired;
        copy.executionMode = executionMode;
        copy.interOpThreads = interOpThreads;
        copy.intraOpThreads = intraOpThreads;
        copy.optimizationLevel = optimizationLevel;
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OnnxEvaluatorOptions that = (OnnxEvaluatorOptions) o;
        return interOpThreads == that.interOpThreads && intraOpThreads == that.intraOpThreads
                && gpuDeviceNumber == that.gpuDeviceNumber && gpuDeviceRequired == that.gpuDeviceRequired
                && optimizationLevel == that.optimizationLevel && executionMode == that.executionMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(optimizationLevel, executionMode, interOpThreads, intraOpThreads, gpuDeviceNumber, gpuDeviceRequired);
    }
}
