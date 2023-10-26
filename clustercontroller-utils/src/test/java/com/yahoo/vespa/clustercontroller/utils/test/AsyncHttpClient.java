// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.test;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpRequest;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpResult;

/**
 * Abstraction of an asynchronous HTTP client, such that applications don't need to depend directly on an HTTP client.
 */
public interface AsyncHttpClient<V extends HttpResult> {

    AsyncOperation<V> execute(HttpRequest r);

    /** Attempt to cancel all pending operations and shut down the client. */
    void close();

}
