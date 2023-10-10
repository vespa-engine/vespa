// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class CompletionTimeoutException extends RuntimeException {

    public CompletionTimeoutException(String errorMessage) {
        super(errorMessage);
    }

}
