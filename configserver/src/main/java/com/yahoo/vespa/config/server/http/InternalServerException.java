// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

/**
 * Exception that will create a http response with INTERNAL_SERVER_ERROR response code (500)
 *
 * @author hmusum
 * @since 5.1.17
 */
public class InternalServerException extends RuntimeException {

    public InternalServerException(String message) {
        super(message);
    }

    public InternalServerException(String message, Exception e) {
        super(message, e);
    }
}
