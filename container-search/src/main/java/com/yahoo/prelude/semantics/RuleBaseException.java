// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics;

/**
 * Thrown on rule base consistency problems
 *
 * @author bratseth
 */
public class RuleBaseException extends RuntimeException {

    public RuleBaseException(String message) {
        super(message);
    }

    public RuleBaseException(String message,Exception cause) {
        super(message, cause);
    }

}
