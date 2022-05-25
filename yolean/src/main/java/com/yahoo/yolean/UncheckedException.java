// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean;

/**
 * Wraps an {@link Exception} with an unchecked exception.
 *
 * @author freva
 */
public class UncheckedException extends RuntimeException {

    public UncheckedException(String message, Exception cause) {
        super(message, cause);
        if (cause instanceof RuntimeException)
            throw new IllegalArgumentException(cause.getClass() + " is not a checked exception");
    }

    public UncheckedException(Exception cause) {
        this(cause.toString(), cause);
    }

    public UncheckedException(String message) { this(message, null); }

}
