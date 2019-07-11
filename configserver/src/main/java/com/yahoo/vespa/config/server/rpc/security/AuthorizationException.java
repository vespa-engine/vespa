// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc.security;

/**
 * @author bjorncs
 */
class AuthorizationException extends RuntimeException {

    AuthorizationException(String message) {
        super(message);
    }

    AuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}

