// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

import com.yahoo.vespa.http.client.Result;

/**
 * Result from a single endpoint.
 *
 * @author dybis
 */
public class EndpointResult {

    private final String operationId;
    private final Result.Detail detail;

    public EndpointResult(String operationId, Result.Detail detail) {
        this.operationId = operationId;
        this.detail = detail;
    }

    public String getOperationId() {
        return operationId;
    }

    public Result.Detail getDetail() {
        return detail;
    }

}
