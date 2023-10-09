// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

/**
 * @author hmusum
 * @since 5.39
 */
public class UnknownVespaVersionException extends RuntimeException {

    public UnknownVespaVersionException(String message) {
        super(message);
    }

    public UnknownVespaVersionException(String message, Throwable e) {
        super(message, e);
    }
}
