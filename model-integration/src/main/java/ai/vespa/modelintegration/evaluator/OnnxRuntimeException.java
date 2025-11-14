// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import ai.onnxruntime.OrtException;

/**
 * @author bjorncs
 */
public class OnnxRuntimeException extends RuntimeException {

    public OnnxRuntimeException(Throwable cause) { super(cause.getMessage(), cause); }

    public OnnxRuntimeException(String message, Throwable cause) { super(message, cause); }

    @Override public synchronized OrtException getCause() { return (OrtException) super.getCause(); }
}
