// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

/**
 * Thrown when language processing receives invalid input from the client.
 *
 * @author bjorncs
 */
public class InvalidInputException extends IllegalArgumentException {

    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(String message, Exception cause) {
        super(message, cause);
    }
}
