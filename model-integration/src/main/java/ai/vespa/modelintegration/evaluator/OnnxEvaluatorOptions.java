// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

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
        executionMode = OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL;
        interOpThreads = 1;
        intraOpThreads = Math.max(1, (int) Math.ceil(((double) Runtime.getRuntime().availableProcessors()) / 4));
        gpuDeviceNumber = -1;
        gpuDeviceRequired = false;
    }

    public OrtSession.SessionOptions getOptions(boolean loadCuda) throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(optimizationLevel);
        options.setExecutionMode(executionMode);
        options.setInterOpNumThreads(interOpThreads);
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
            executionMode = OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL;
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

    public void setGpuDevice(int deviceNumber, boolean required) {
        this.gpuDeviceNumber = deviceNumber;
        this.gpuDeviceRequired = required;
    }

    public boolean requestingGpu() {
        return gpuDeviceNumber > -1;
    }

    public boolean gpuDeviceRequired() {
        return gpuDeviceRequired;
    }

}
