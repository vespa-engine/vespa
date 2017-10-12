// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz;

import com.yahoo.athenz.zts.ZTSClientException;

/**
 * @author bjorncs
 */
public class ZtsException extends RuntimeException {

    private final int code;

    public ZtsException(ZTSClientException e) {
        super(e.getMessage(), e);
        this.code = e.getCode();
    }


    public int getCode() {
        return code;
    }
}
