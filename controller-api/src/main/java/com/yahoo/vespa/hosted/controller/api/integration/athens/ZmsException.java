// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athens;

/**
 * @author bjorncs
 */
public class ZmsException extends RuntimeException {

    private final int code;

    public ZmsException(Throwable t, int code) {
        super(t.getMessage(), t);
        this.code = code;
    }

    public ZmsException(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
