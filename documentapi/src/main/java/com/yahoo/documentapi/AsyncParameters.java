// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

/**
 * Parameters for creating an async session
 *
 * @author bratseth
 */
public class AsyncParameters extends Parameters {

    private ResponseHandler responseHandler = null;

    public ResponseHandler getResponseHandler() {
        return responseHandler;
    }

    public AsyncParameters setResponseHandler(ResponseHandler responseHandler) {
        this.responseHandler = responseHandler;
        return this;
    }
}
