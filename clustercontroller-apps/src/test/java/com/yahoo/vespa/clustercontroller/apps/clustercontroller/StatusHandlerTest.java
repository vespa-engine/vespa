// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import junit.framework.TestCase;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class StatusHandlerTest extends TestCase {

    public void testSimple() {
        ClusterController controller = new ClusterController();
        StatusHandler handler = new StatusHandler(controller, StatusHandler.testOnlyContext());
    }

}
