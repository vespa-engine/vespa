// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.engine;

/**
 * Thrown on semantic exceptions on evaluation over a rule base
 *
 * @author bratseth
 */
@SuppressWarnings("serial")
public class EvaluationException extends RuntimeException {

    public EvaluationException(String message) {
        super(message);
    }
}
