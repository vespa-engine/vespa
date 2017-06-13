// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.google.inject.Inject;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.vespa.clustercontroller.apputil.communication.http.JDiscHttpRequestHandler;

import java.util.concurrent.Executor;

public class StatusHandler extends JDiscHttpRequestHandler {
    private final com.yahoo.vespa.clustercontroller.core.status.StatusHandler statusHandler;

    @Inject
    public StatusHandler(ClusterController fc, Executor executor, AccessLog accessLog) {
        this(new com.yahoo.vespa.clustercontroller.core.status.StatusHandler(fc), executor, accessLog);
    }

    private StatusHandler(com.yahoo.vespa.clustercontroller.core.status.StatusHandler handler, Executor executor, AccessLog accessLog) {
        super(handler, executor, accessLog);
        this.statusHandler = handler;
    }
}
