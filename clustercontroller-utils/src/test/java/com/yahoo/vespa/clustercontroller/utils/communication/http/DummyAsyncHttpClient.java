// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperationImpl;

public class DummyAsyncHttpClient implements AsyncHttpClient<HttpResult> {
    HttpResult result;
    HttpRequest lastRequest;

    public DummyAsyncHttpClient(HttpResult result) {
        this.result = result;
    }

    @Override
    public AsyncOperation<HttpResult> execute(HttpRequest r) {
        lastRequest = r;
        AsyncOperationImpl<HttpResult> op = new AsyncOperationImpl<>(r.toString());
        op.setResult(result);
        return op;
    }

    @Override
    public void close() {
    }
}
