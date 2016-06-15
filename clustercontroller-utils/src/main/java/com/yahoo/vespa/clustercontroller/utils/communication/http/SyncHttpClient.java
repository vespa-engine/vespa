// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

public interface SyncHttpClient {
    public HttpResult execute(HttpRequest r);

    /** Attempt to cancel all pending operations and shut down the client. */
    public void close();
}
