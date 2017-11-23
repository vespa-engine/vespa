// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

/**
 * Exceptions related to proxying calls to config servers.
 *
 * @author Haakon Dybdahl
 */
public class ProxyException extends Exception {
    public final ErrorResponse errorResponse;

    public ProxyException(ErrorResponse errorResponse) {
        super(errorResponse.message);
        this.errorResponse = errorResponse;
    }
}
