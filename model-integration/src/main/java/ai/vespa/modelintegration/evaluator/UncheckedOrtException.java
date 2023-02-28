// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import ai.onnxruntime.OrtException;

/**
 * @author bjorncs
 */
public class UncheckedOrtException extends RuntimeException {

    public UncheckedOrtException(Throwable e) { super(e.getMessage(), e); }

    @Override public synchronized OrtException getCause() { return (OrtException) super.getCause(); }
}
