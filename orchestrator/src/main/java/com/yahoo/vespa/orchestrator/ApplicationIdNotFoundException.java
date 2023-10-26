// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

/**
 * Thrown when applicationId is invalid or not found.
 *
 * @author smorgrav
 */
public class ApplicationIdNotFoundException extends Exception {

    public ApplicationIdNotFoundException() {
        super();
    }

    public ApplicationIdNotFoundException(String reason) {
        super(reason);
    }

}
