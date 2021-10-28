// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * A provisioning exception that is considered transient. Exceptions that we expect to recover from after a short
 * duration should extend this. Throwing a subclass of this indicates that the operation can safely be retried.
 *
 * @author mpolden
 */
public abstract class TransientException extends RuntimeException {

    public TransientException(String message) {
        super(message);
    }

    public TransientException(String message, Throwable cause) {
        super(message, cause);
    }

}
