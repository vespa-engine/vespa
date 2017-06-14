// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;

/**
 * Abstraction of an asynchronious HTTP client, such that applications don't need to depend directly on an HTTP client.
 */
public interface AsyncHttpClient<V extends HttpResult> {

    public AsyncOperation<V> execute(HttpRequest r);

    /** Attempt to cancel all pending operations and shut down the client. */
    public void close();

}
