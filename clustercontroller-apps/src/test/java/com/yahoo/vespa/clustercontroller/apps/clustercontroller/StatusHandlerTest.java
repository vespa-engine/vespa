// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.yahoo.container.logging.AccessLog;
import junit.framework.TestCase;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class StatusHandlerTest extends TestCase {

    public void testSimple() {
        ClusterController controller = new ClusterController();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 100, 100, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1000));
        StatusHandler handler = new StatusHandler(controller, executor, AccessLog.voidAccessLog());
        executor.shutdown();
    }
}
