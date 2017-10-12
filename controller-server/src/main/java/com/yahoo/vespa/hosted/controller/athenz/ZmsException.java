// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz;

import com.yahoo.athenz.zms.ZMSClientException;

/**
 * @author bjorncs
 */
public class ZmsException extends RuntimeException {

    private final int code;

    public ZmsException(ZMSClientException e) {
        super(e.getMessage(), e);
        this.code = e.getCode();
    }


    public int getCode() {
        return code;
    }
}
