// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

/**
 * Thrown when language processing component is overloaded or rate-limited.
 *
 * @author bjorncs
 */
public class OverloadException extends RuntimeException {

    public OverloadException(String message) {
        super(message);
    }

    public OverloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
