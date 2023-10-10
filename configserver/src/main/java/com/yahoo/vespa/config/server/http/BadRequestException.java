// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

/**
 * Exception that will create a http response with BAD_REQUEST response code (400)
 *
 * @author hmusum
 */
public class BadRequestException extends IllegalArgumentException {

    public BadRequestException(String message) {
        super(message);
    }

}
