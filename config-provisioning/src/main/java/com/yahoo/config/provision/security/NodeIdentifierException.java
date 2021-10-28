// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.security;

/**
 * @author bjorncs
 */
public class NodeIdentifierException extends RuntimeException {

    public NodeIdentifierException(String message) {
        super(message);
    }

    public NodeIdentifierException(String message, Throwable cause) {
        super(message, cause);
    }

    public NodeIdentifierException(Throwable cause) {
        super(cause);
    }
}
