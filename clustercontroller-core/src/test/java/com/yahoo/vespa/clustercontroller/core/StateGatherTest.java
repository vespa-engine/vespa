// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.logging.Level;
import org.junit.Test;

import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class StateGatherTest extends FleetControllerTest {

    public static Logger log = Logger.getLogger(StateGatherTest.class.getName());

    private String getGetNodeStateReplyCounts(DummyVdsNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append("timedout ").append(node.timedOutStateReplies)
          .append(", outdated ").append(node.outdatedStateReplies)
          .append(", immediate ").append(node.immediateStateReplies)
          .append(", setstate ").append(node.setNodeStateReplies)
          .append(", pending ").append(node.getPendingNodeStateCount());
        return sb.toString();
    }

    @Test
    public void testAlwaysHavePendingGetNodeStateRequestTowardsNodes() throws Exception {
        Logger.getLogger(NodeStateGatherer.class.getName()).setLevel(Level.FINEST);
        startingTest("StateGatherTest::testOverlappingGetNodeStateRequests");
        FleetControllerOptions options = defaultOptions("mycluster");
        options.nodeStateRequestTimeoutMS = 10 * 60 * 1000;
        // Force actual message timeout to be lower than request timeout.
        options.nodeStateRequestTimeoutEarliestPercentage = 80;
        options.nodeStateRequestTimeoutLatestPercentage = 80;
        setUpFleetController(true, options);
        String[] connectionSpecs = new String[1];
        connectionSpecs[0] = "tcp/localhost:" + slobrok.port();
        DummyVdsNodeOptions dummyOptions = new DummyVdsNodeOptions();
        DummyVdsNode dnode = new DummyVdsNode(timer, dummyOptions, connectionSpecs, this.options.clusterName, true, 0);
        DummyVdsNode snode = new DummyVdsNode(timer, dummyOptions, connectionSpecs, this.options.clusterName, false, 0);
        dnode.connect();
        snode.connect();

        waitUntilPendingGetNodeState(dnode, snode);

        assertEquals("timedout 0, outdated 0, immediate 1, setstate 0, pending 1", getGetNodeStateReplyCounts(dnode));
        assertEquals("timedout 0, outdated 0, immediate 1, setstate 0, pending 1", getGetNodeStateReplyCounts(snode));

        waitForCompleteCycle();
        timer.advanceTime(9 * 60 * 1000); // Requests should have timed out on nodes (8 min timeout).

        waitUntilTimedOutGetNodeState(dnode, snode);
        waitForCompleteCycle(); // Send new node state requests.
        waitUntilPendingGetNodeState(dnode, snode);

        assertEquals("timedout 1, outdated 0, immediate 1, setstate 0, pending 1", getGetNodeStateReplyCounts(dnode));
        assertEquals("timedout 1, outdated 0, immediate 1, setstate 0, pending 1", getGetNodeStateReplyCounts(snode));
    }

    private void waitUntilTimedOutGetNodeState(DummyVdsNode dnode, DummyVdsNode snode) throws TimeoutException {
        long timeout = System.currentTimeMillis() + timeoutMS;
        synchronized (timer) {
            while (dnode.timedOutStateReplies != 1 || snode.timedOutStateReplies != 1) {
                if (System.currentTimeMillis() > timeout) {
                    throw new TimeoutException("Did not get to have one timed out within timeout of " + timeoutMS + " ms"
                            + ", " + getGetNodeStateReplyCounts(dnode) + ", " + getGetNodeStateReplyCounts(snode));
                }
                try{ timer.wait(1); } catch (InterruptedException e) { /* ignore */ }
            }
        }
    }

    private void waitUntilPendingGetNodeState(DummyVdsNode dnode, DummyVdsNode snode) throws TimeoutException {
        long timeout = System.currentTimeMillis() + timeoutMS;
        while (dnode.getPendingNodeStateCount() != 1 || snode.getPendingNodeStateCount() != 1) {
            if (System.currentTimeMillis() > timeout) throw new TimeoutException("Did not get to have one pending within timeout of " + timeoutMS + " ms");
            try{ Thread.sleep(1); } catch (InterruptedException e) { /* ignore */ }
        }
    }

}
