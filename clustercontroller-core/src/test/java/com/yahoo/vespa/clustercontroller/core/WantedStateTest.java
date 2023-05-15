// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.vdslib.state.State;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(CleanupZookeeperLogsOnSuccess.class)
public class WantedStateTest extends FleetControllerTest {

    private Supervisor supervisor;
    private final Timer timer = new FakeTimer();

    @BeforeEach
    public void setup() {
        supervisor = new Supervisor(new Transport());
    }

    @AfterEach
    public void teardown() {
        supervisor.transport().shutdown().join();
    }

    @Test
    void testSettingStorageNodeMaintenanceAndBack() throws Exception {
        setUpFleetController(timer, defaultOptions());
        setUpVdsNodes(timer);
        waitForStableSystem();

        setWantedState(nodes.get(1), State.MAINTENANCE, null, supervisor);
        waitForState("version:\\d+ distributor:10 storage:10 .0.s:m");

        setWantedState(nodes.get(1), State.UP, null, supervisor);
        waitForState("version:\\d+ distributor:10 storage:10");
    }

    @Test
    void testOverridingWantedStateOtherReason() throws Exception {
        setUpFleetController(timer, defaultOptions());
        setUpVdsNodes(timer);
        waitForStableSystem();

        setWantedState(nodes.get(1), State.MAINTENANCE, "Foo", supervisor);
        waitForState("version:\\d+ distributor:10 storage:10 .0.s:m");
        assertEquals("Foo", fleetController().getWantedNodeState(nodes.get(1).getNode()).getDescription());

        setWantedState(nodes.get(1), State.MAINTENANCE, "Bar", supervisor);
        waitForCompleteCycle();
        assertEquals("Bar", fleetController().getWantedNodeState(nodes.get(1).getNode()).getDescription());

        setWantedState(nodes.get(1), State.UP, null, supervisor);
        waitForState("version:\\d+ distributor:10 storage:10");
    }


}
