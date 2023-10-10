// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.exception;

/**
 * Exception used when activation cannot be done because activation is for
 * an older session than the one that is active now or because current active
 * session has changed since the session to be activated was created
 *
 * @author hmusum
 */
public class ActivationConflictException extends RuntimeException {

    public ActivationConflictException(String message) { super(message); }

    public ActivationConflictException(String message, Throwable cause) { super(message, cause); }

}
