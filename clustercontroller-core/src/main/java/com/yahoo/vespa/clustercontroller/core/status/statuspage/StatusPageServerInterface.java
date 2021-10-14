// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status.statuspage;

public interface StatusPageServerInterface {

    int getPort();
    void shutdown() throws InterruptedException, java.io.IOException;
    void setPort(int port) throws java.io.IOException, InterruptedException;
    StatusPageServer.HttpRequest getCurrentHttpRequest();
    void answerCurrentStatusRequest(StatusPageResponse r);

}
