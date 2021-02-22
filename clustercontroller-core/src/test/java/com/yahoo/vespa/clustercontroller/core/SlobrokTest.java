// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.slobrok.server.Slobrok;
import java.util.logging.Level;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.logging.Logger;

public class SlobrokTest extends FleetControllerTest {

    private static final Logger log = Logger.getLogger(SlobrokTest.class.getName());

    @Test
    public void testSingleSlobrokRestart() throws Exception {
        startingTest("SlobrokTest::testSingleSlobrokRestart");
        FleetControllerOptions options = defaultOptions("mycluster");
        options.nodeStateRequestTimeoutMS = 60 * 60 * 1000;
        options.maxSlobrokDisconnectGracePeriod = 60 * 60 * 1000;
        setUpFleetController(true, options);
        setUpVdsNodes(true, new DummyVdsNodeOptions());
        waitForStableSystem();

        int version = fleetController.getSystemState().getVersion();
        int slobrokPort = slobrok.port();

            // Test that we survive some slobrok instability without changing system state.
        for (int j=0; j<4; ++j) {
            log.log(Level.INFO, "Mirror updateForDistributor count is " + fleetController.getSlobrokMirrorUpdates());
            log.log(Level.INFO, "STOPPING SLOBROK SERVER (" + (j+1) + "/4)");
            slobrok.stop();
            for (int i=0; i<10; ++i) {
                    // Force one node to at least notice that the slobrok server is gone
                if (i == 5) {
                    log.log(Level.INFO, "Forcing one node to initate a resend: " + nodes.get(3));
                    nodes.get(3).replyToPendingNodeStateRequests();
                }
                waitForCompleteCycle();
                timer.advanceTime(100);
            }
            log.log(Level.INFO, "STARTING SLOBROK SERVER AGAIN (" + (j+1) + "/4)");
            slobrok = new Slobrok(slobrokPort);
                // May take up to 30 seconds for slobrok clients to re-register. Trigger retry.
            for (DummyVdsNode node : nodes) {
                node.disconnectSlobrok();
                node.registerSlobrok();
            }
            //fleetController.setFreshSlobrokMirror();
            waitForCompleteCycle();
            fleetController.waitForNodesInSlobrok(10, 10, timeoutMS);

            log.log(Level.INFO, "Waiting for cluster to be up and available again");
            for (int i = 0; i < timeoutMS; i += 10) {
                if (clusterAvailable()) break;
                timer.advanceTime(1000);
                waitForCompleteCycle();
                try{
                    Thread.sleep(10);
                } catch (InterruptedException e) { /* ignore */ }
            }
            assertClusterAvailable();
        }

        assertEquals("Cluster state was affected, although it should not have been.",
                     version, fleetController.getSystemState().getVersion());
    }

    @Test
    public void testNodeTooLongOutOfSlobrok() throws Exception {
        startingTest("SlobrokTest::testNodeTooLongOutOfSlobrok");
        FleetControllerOptions options = defaultOptions("mycluster");
        options.maxSlobrokDisconnectGracePeriod = 60 * 1000;
        options.nodeStateRequestTimeoutMS = 10000 * 60 * 1000;
        setUpFleetController(true, options);
        setUpVdsNodes(true, new DummyVdsNodeOptions());
        waitForStableSystem();

        int version = fleetController.getSystemState().getVersion();
        nodes.get(0).disconnectSlobrok();
        log.log(Level.INFO, "DISCONNECTED NODE FROM SLOBROK. SHOULD BE IN COOLDOWN PERIOD");
        fleetController.waitForNodesInSlobrok(9, 10, timeoutMS);
        synchronized (timer) {
            nodes.get(0).sendGetNodeStateReply(0);
        }

        // Give system a little time to possible faultily removing node not in slobrok
        timer.advanceTime(1000);
        try{ Thread.sleep(10); } catch (InterruptedException e) { /* ignore */ }
        assertEquals(version, fleetController.getSystemState().getVersion());
        log.log(Level.INFO, "JUMPING TIME. NODE SHOULD BE MARKED DOWN");
        // At this point the fleetcontroller might not have noticed that the node is out of slobrok yet.
        // Thus we keep advancing time another minute such that it should get down.
        timer.advanceTime(options.nodeStateRequestTimeoutMS + options.maxSlobrokDisconnectGracePeriod);
        waitForState("version:\\d+ distributor:10 .0.s:d storage:10");
    }

    private boolean clusterAvailable() {
        boolean ok = true;
        ContentCluster cluster = fleetController.getCluster();
        for (NodeInfo info : cluster.getNodeInfo()) {
            if (info.getConnectionAttemptCount() > 0) ok = false;
            if (info.getLatestNodeStateRequestTime() == null) ok = false;
        }
        return ok;
    }
    private void assertClusterAvailable() {
        ContentCluster cluster = fleetController.getCluster();
        for (NodeInfo info : cluster.getNodeInfo()) {
            assertEquals("Node " + info + " connection attempts.", 0, info.getConnectionAttemptCount());
            assertTrue("Node " + info + " has no last request time.", info.getLatestNodeStateRequestTime() != 0);
        }
    }
}
