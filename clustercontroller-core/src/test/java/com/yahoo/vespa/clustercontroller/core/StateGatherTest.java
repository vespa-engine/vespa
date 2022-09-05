// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.NodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(CleanupZookeeperLogsOnSuccess.class)
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
    void testAlwaysHavePendingGetNodeStateRequestTowardsNodes() throws Exception {
        Logger.getLogger(NodeStateGatherer.class.getName()).setLevel(Level.FINEST);
        startingTest("StateGatherTest::testOverlappingGetNodeStateRequests");
        FleetControllerOptions.Builder builder = defaultOptions("mycluster")
                .setNodeStateRequestTimeoutMS(10 * 60 * 1000)
                // Force actual message timeout to be lower than request timeout.
                .setNodeStateRequestTimeoutEarliestPercentage(80)
                .setNodeStateRequestTimeoutLatestPercentage(80);
        setUpFleetController(true, builder);
        String[] connectionSpecs = getSlobrokConnectionSpecs(slobrok);
        DummyVdsNode dnode = new DummyVdsNode(timer, connectionSpecs, builder.clusterName(), NodeType.DISTRIBUTOR, 0);
        DummyVdsNode snode = new DummyVdsNode(timer, connectionSpecs, builder.clusterName(), NodeType.STORAGE, 0);
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
        Instant endTime = Instant.now().plus(timeout());
        synchronized (timer) {
            while (dnode.timedOutStateReplies != 1 || snode.timedOutStateReplies != 1) {
                if (Instant.now().isAfter(endTime)) {
                    throw new TimeoutException("Did not get to have one timed out within timeout of " + timeout()
                            + ", " + getGetNodeStateReplyCounts(dnode) + ", " + getGetNodeStateReplyCounts(snode));
                }
                try{ timer.wait(1); } catch (InterruptedException e) { /* ignore */ }
            }
        }
    }

    private void waitUntilPendingGetNodeState(DummyVdsNode dnode, DummyVdsNode snode) throws TimeoutException {
        Instant endTime = Instant.now().plus(timeout());
        while (dnode.getPendingNodeStateCount() != 1 || snode.getPendingNodeStateCount() != 1) {
            if (Instant.now().isAfter(endTime)) throw new TimeoutException("Did not get to have one pending within timeout of " + timeout());
            try{ Thread.sleep(1); } catch (InterruptedException e) { /* ignore */ }
        }
    }

}
