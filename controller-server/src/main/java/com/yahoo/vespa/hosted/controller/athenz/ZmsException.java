// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz;

/**
 * @author bjorncs
 */
public class ZmsException extends RuntimeException {

    private final int code;

    public ZmsException(int code, Throwable cause) {
        super(cause.getMessage(), cause);
        this.code = code;
    }

    public ZmsException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
