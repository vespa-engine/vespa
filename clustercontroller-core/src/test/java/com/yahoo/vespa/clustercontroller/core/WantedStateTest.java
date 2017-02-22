// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.*;
import com.yahoo.jrt.StringValue;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WantedStateTest extends FleetControllerTest {

    private Supervisor supervisor;

    @Before
    public void setUp() {
        supervisor = new Supervisor(new Transport());
    }

    @After
    public void tearDown() throws Exception {
        if (supervisor != null) {
            supervisor.transport().shutdown().join();
            supervisor = null;
        }
        super.tearDown();
    }

    public void setWantedState(DummyVdsNode node, State state, String reason) {
        NodeState ns = new NodeState(node.getType(), state);
        if (reason != null) ns.setDescription(reason);
        Target connection = supervisor.connect(new Spec("localhost", fleetController.getRpcPort()));
        Request req = new Request("setNodeState");
        req.parameters().add(new StringValue(node.getSlobrokName()));
        req.parameters().add(new StringValue(ns.serialize()));
        connection.invokeSync(req, timeoutS);
        if (req.isError()) {
            assertTrue("Failed to invoke setNodeState(): " + req.errorCode() + ": " + req.errorMessage(), false);
        }
        if (!req.checkReturnTypes("s")) {
            assertTrue("Failed to invoke setNodeState(): Invalid return types.", false);
        }
    }

    @Test
    public void testSettingStorageNodeMaintenanceAndBack() throws Exception {
        startingTest("WantedStateTest::testSettingStorageNodeMaintenanceAndBack()");
        setUpFleetController(true, new FleetControllerOptions("mycluster"));
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
        setUpFleetController(true, new FleetControllerOptions("mycluster"));
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
