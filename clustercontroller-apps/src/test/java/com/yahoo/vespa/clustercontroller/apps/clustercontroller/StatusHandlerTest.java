// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import org.junit.Test;

public class StatusHandlerTest {

    @Test
    public void testSimple() {
        ClusterController controller = new ClusterController();
        StatusHandler handler = new StatusHandler(controller, StatusHandler.testOnlyContext());
    }

}
