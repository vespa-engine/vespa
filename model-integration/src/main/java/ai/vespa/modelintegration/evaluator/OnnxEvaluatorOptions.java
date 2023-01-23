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

    public OrtSession.SessionOptions getOptions() throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(optimizationLevel);
        options.setExecutionMode(executionMode);
        options.setInterOpNumThreads(interOpThreads);
        options.setIntraOpNumThreads(intraOpThreads);
        addCuda(options);
        return options;
    }

    private void addCuda(OrtSession.SessionOptions options) throws OrtException {
        if (gpuDeviceNumber < 0) return;
        try {
            options.addCUDA(gpuDeviceNumber);
        } catch (OrtException e) {
            if (e.getCode() != OrtException.OrtErrorCode.ORT_EP_FAIL) {
                throw e;
            }
            if (gpuDeviceRequired) {
                throw new IllegalArgumentException("GPU device " + gpuDeviceNumber + " is required, but CUDA backend could not be initialized", e);
            }
        }
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

}
