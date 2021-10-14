// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.State;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class WantedStateTest extends FleetControllerTest {

    @Test
    public void testSettingStorageNodeMaintenanceAndBack() throws Exception {
        startingTest("WantedStateTest::testSettingStorageNodeMaintenanceAndBack()");
        setUpFleetController(true, defaultOptions("mycluster"));
        setUpVdsNodes(true, new DummyVdsNodeOptions());
        waitForStableSystem();

        setWantedState(nodes.get(1), State.MAINTENANCE, null);
        waitForState("version:\\d+ distributor:10 storage:10 .0.s:m");

        setWantedState(nodes.get(1), State.UP, null);
        waitForState("version:\\d+ distributor:10 storage:10");
    }

    @Test
    public void testOverridingWantedStateOtherReason() throws Exception {
        startingTest("WantedStateTest::testOverridingWantedStateOtherReason()");
        setUpFleetController(true, defaultOptions("mycluster"));
        setUpVdsNodes(true, new DummyVdsNodeOptions());
        waitForStableSystem();

        setWantedState(nodes.get(1), State.MAINTENANCE, "Foo");
        waitForState("version:\\d+ distributor:10 storage:10 .0.s:m");
        assertEquals("Foo", fleetController.getWantedNodeState(nodes.get(1).getNode()).getDescription());

        setWantedState(nodes.get(1), State.MAINTENANCE, "Bar");
        waitForCompleteCycle();
        assertEquals("Bar", fleetController.getWantedNodeState(nodes.get(1).getNode()).getDescription());

        setWantedState(nodes.get(1), State.UP, null);
        waitForState("version:\\d+ distributor:10 storage:10");
    }


}
