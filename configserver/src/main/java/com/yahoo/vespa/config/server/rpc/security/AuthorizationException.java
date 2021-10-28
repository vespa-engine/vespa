// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc.security;

/**
 * @author bjorncs
 */
class AuthorizationException extends RuntimeException {

    enum Type {WARN, SILENT}

    private final Type type;

    AuthorizationException(Type type, String message) {
        super(message);
        this.type = type;
    }

    AuthorizationException(String message) {
        this(Type.WARN, message);
    }

    AuthorizationException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    AuthorizationException(String message, Throwable cause) {
        this(Type.WARN, message, cause);
    }

    Type type() { return type; }
}

