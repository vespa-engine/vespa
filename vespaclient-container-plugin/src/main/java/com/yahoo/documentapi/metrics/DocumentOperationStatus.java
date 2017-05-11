// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.metrics;

import com.yahoo.document.restapi.OperationHandlerImpl;

import java.util.Set;

/**
 * @author freva
 */
public enum  DocumentOperationStatus {
    OK, REQUEST_ERROR, SERVER_ERROR;

    public static DocumentOperationStatus fromHttpStatusCode(int httpStatus) {
        switch (httpStatus / 100) {
            case 2:
                return OK;

            case 4:
                return REQUEST_ERROR;

            case 5:
                return SERVER_ERROR;

            default:
                return null;
        }
    }

    public static DocumentOperationStatus fromMessageBusErrorCodes(Set<Integer> errorCodes) {
        return fromHttpStatusCode(OperationHandlerImpl.getHTTPStatusCode(errorCodes));
    }
}
