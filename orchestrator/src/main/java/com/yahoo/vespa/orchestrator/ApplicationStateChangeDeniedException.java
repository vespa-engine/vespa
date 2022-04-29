// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

/**
 * Exception covering all cases where the state change could not
 * be executed.
 *
 * @author smorgrav
 */
public class ApplicationStateChangeDeniedException extends Exception {

    public ApplicationStateChangeDeniedException(String reason) {
        super(reason);
    }

}
