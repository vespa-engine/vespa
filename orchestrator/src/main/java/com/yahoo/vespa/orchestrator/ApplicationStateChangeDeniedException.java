// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

/**
 * Exception covering all cases where the state change could not
 * be executed.
 *
 * @author smorgrav
 */
public class ApplicationStateChangeDeniedException extends Exception {

    final String reason;

    public ApplicationStateChangeDeniedException(String reason) {
        super();
        this.reason = reason;
    }

}
