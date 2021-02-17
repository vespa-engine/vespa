// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing;

/**
 * Thrown on illegal input received from the requesting client.
 * Use this instead of the superclass, IllegalArgumentException
 * to signal illegal input to the client without causing logging and stack traces,
 *
 * @author bratseth
 */
public class IllegalInputException extends IllegalArgumentException {

    public IllegalInputException(String message) {
        super(message);
    }

    public IllegalInputException(Throwable cause) {
        super(cause);
    }

    public IllegalInputException(String message, Throwable cause) {
        super(message, cause);
    }

}
