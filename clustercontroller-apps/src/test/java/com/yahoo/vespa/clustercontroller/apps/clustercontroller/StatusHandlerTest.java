// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import org.junit.jupiter.api.Test;

public class StatusHandlerTest {

    @Test
    void testSimple() {
        ClusterController controller = new ClusterController();
        StatusHandler handler = new StatusHandler(controller, StatusHandler.testContext());
    }

}
