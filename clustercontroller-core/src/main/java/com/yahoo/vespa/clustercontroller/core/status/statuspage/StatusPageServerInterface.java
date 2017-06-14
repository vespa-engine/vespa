// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status.statuspage;

public interface StatusPageServerInterface {
    public int getPort();
    public void shutdown() throws InterruptedException, java.io.IOException;
    public void setPort(int port) throws java.io.IOException, InterruptedException;
    public StatusPageServer.HttpRequest getCurrentHttpRequest();
    public void answerCurrentStatusRequest(StatusPageResponse r);
}
