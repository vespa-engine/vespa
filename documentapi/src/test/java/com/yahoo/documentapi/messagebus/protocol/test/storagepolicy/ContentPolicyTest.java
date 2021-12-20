// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test.storagepolicy;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.routing.RoutingNode;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ContentPolicyTest extends Simulator {

    /**
     * Verify that a resent message with failures doesn't ruin overall performance. (By dumping the cached state too often
     * so other requests are sent to wrong target)
     * Lets one node always fail message with transient error.
     */
    @Test
    @Ignore // FIXME test has been implicitly disabled for ages, figure out and fix
    public void testPersistentFailureTransientError() {
        runSimulation("First correctnode 99, wrongnode 1, downnode 0, worked 90, failed 10 "
                    + "Last correctnode 99, wrongnode 1, downnode 0, worked 92, failed 8",
                      new PersistentFailureTestParameters().addBadNode(new BadNode(3, FailureType.TRANSIENT_ERROR)));
    }

    /**
     * Verify that a resent message with failures doesn't ruin overall performance. (By dumping the cached state too often
     * so other requests are sent to wrong target)
     * Lets one node always fail message with fatal error.
     */
    @Test
    @Ignore // FIXME test has been implicitly disabled for ages, figure out and fix
    public void testPersistentFailureFatalError() {
        runSimulation("First correctnode 99, wrongnode 1, downnode 0, worked 90, failed 10 "
                    + "Last correctnode 99, wrongnode 1, downnode 0, worked 92, failed 8",
                      new PersistentFailureTestParameters().addBadNode(new BadNode(3, FailureType.FATAL_ERROR)));
    }

    /**
     * Verify that a node responding with old cluster state doesn't ruin overall performance (By dumping/switching cached
     * state too often)
     * Let one node reporting an old cluster state (but node is still set up in fleetcontroller state).
     * We expect some requests to go to wrong node due to this issue, but the majority of requests should be unaffected.
     */
    @Test
    public void testPersistentFailureOldClusterState() {
        runSimulation("First correctnode .*, wrongnode .*, downnode .*, worked .*, failed .* "
                    + "Last correctnode 100, wrongnode 0, downnode 0, worked 100, failed 0",
                      new PersistentFailureTestParameters().addBadNode(new BadNode(3, FailureType.OLD_CLUSTER_STATE).setDownInCurrentState()));
    }

    /**
     * Verify that a reset cluster state version doesn't keep sending requests to the wrong node.
     * We expect a few failures in first half. We should have detected the issue before second half, so there all should be fine.
     */
    @Test
    public void testPersistentFailureResetClusterState() {
        // If reset detection works (within the few messages sent in test), we should not fail any requests or send to wrong nodes in second half
        runSimulation("First correctnode .*, wrongnode .*, downnode .*, worked .*, failed .* "
                    + "Last correctnode .*, wrongnode 0, downnode 0, worked .*, failed 0",
                      new PersistentFailureTestParameters().addBadNode(new BadNode(3, FailureType.RESET_CLUSTER_STATE).setDownInCurrentState()));
    }

    /**
     * Verify that a reset cluster state version doesn't keep sending requests to the wrong node.
     * We expect a few failures in first half. We should have detected the issue before second half, so there all should be fine.
     */
    @Test
    public void testPersistentFailureResetClusterStateNoGoodNodes() {
        // If reset detection works (within the few messages sent in test), we should not fail any requests in second half.

        // Current problem here, is that even though we from time to time will send requests to other nodes, and will eventually throw the faulty cluster state,
        // we will have pending operations towards this distributor when it happens, so it very quickly returns into a bad state.

        // This issue should hopefully not be that critical as we don't expect nodes to stay up and report erronious states. Even nodes that are down do get the
        // cluster states sent to them, and if that doesn't work, how do the client manage to talk to them?

        runSimulation("First correctnode .*, wrongnode .*, downnode .*, worked .*, failed .* "
                    + "Last correctnode .*, wrongnode 100, downnode 100, worked 0, failed 100",
                      new PersistentFailureTestParameters().addBadNode(new BadNode(3, FailureType.RESET_CLUSTER_STATE_NO_GOOD_NODES).setDownInCurrentState()));
    }

    /**
     * Verify that a reset cluster state version doesn't keep sending requests to the wrong node.
     * We expect a few failures in first half. We should have detected the issue before second half, so there all should be fine.
     */
    @Test
    @Ignore // FIXME test has been implicitly disabled for ages, figure out and fix
    public void testPersistentFailureResetClusterStateNoGoodNodesNotMarkedDown() {
        // If reset detection works (within the few messages sent in test), we should not fail any requests in second half.

        // This is just as sad as the above. Even if the node got detected to be screwed, we'd still be in the setting above. We don't expect nodes
        // to get into this state however.

        runSimulation("First correctnode .*, wrongnode .*, downnode .*, worked .*, failed .* "
                    + "Last correctnode .*, wrongnode 91, downnode 0, worked 0, failed 100",
                      new PersistentFailureTestParameters().addBadNode(new BadNode(3, FailureType.RESET_CLUSTER_STATE_NO_GOOD_NODES)));
    }

    /**
     * Verify that a reset cluster state version doesn't keep sending requests to the wrong node.
     * Another scenario where we have a node coming up in correct state.
     * We expect a few failures in first half. We should have detected the issue before second half, so there all should be fine.
     */
    @Test
    public void testPersistentFailureResetClusterStateNewNodeUp() {
        // If we handled this well, we should have no failing requests, and no requests to down node in second half
        runSimulation("First correctnode .*, wrongnode .*, downnode .*, worked .*, failed .* "
                    + "Last correctnode .*, wrongnode 0, downnode 0, worked .*, failed 0",
                      new PersistentFailureTestParameters().newNodeAdded().addBadNode(new BadNode(3, FailureType.RESET_CLUSTER_STATE).setDownInCurrentState()));
    }

    /** Test node that is not in slobrok. Until fleetcontroller detects this, we expect 10% of the requests to go to wrong node. */
    @Test
    @Ignore // FIXME test has been implicitly disabled for ages, figure out and fix
    public void testPersistentFailureNodeNotInSlobrok() {
        runSimulation("First correctnode .*, wrongnode 11, downnode 0, worked .*, failed .* "
                    + "Last correctnode .*, wrongnode 9, downnode 0, worked 100, failed 0",
                      new PersistentFailureTestParameters().addBadNode(new BadNode(3, FailureType.NODE_NOT_IN_SLOBROK)));
    }

    /** With two failures, one marked down, hopefully the one not marked down doesn't lead us to use the one marked down. */
    @Test
    @Ignore // FIXME test has been implicitly disabled for ages, figure out and fix
    public void testPersistentFailureTwoNodesFailingOneMarkedDown() {
            // We see that we don't send to down nodes in second half. We still fail requests towards the one not marked down,
            // and occasionally send to random due to this
        runSimulation("First correctnode .*, wrongnode 23, downnode .*, worked .*, failed .* "
                    + "Last correctnode .*, wrongnode 4, downnode 0, worked 219, failed 31",
                      new PersistentFailureTestParameters().addBadNode(new BadNode(3, FailureType.TRANSIENT_ERROR))
                                                           .addBadNode(new BadNode(4, FailureType.TRANSIENT_ERROR).setDownInCurrentState())
                                                           .setTotalRequests(500));
            // Note that we use extra requests here as with only 200 requests there was a pretty good chance of not going to any down node on random anyhow.
    }

    private void setUpSingleNodeFixturesWithInitializedPolicy() {
        setClusterNodes(new int[]{ 0 });
        // Seed policy with initial, correct cluster state.
        {
            RoutingNode target = select();
            replyWrongDistribution(target, "foo", null, "version:1234 distributor:1 storage:1");
        }
    }

    @Test
    public void transient_errors_expected_during_normal_feed_are_not_counted_as_errors_that_may_trigger_random_send() {
        setUpSingleNodeFixturesWithInitializedPolicy();
        var checker = policyFactory.getLastParameters().instabilityChecker;
        assertEquals(0, checker.recordedFailures); // WrongDistributionReply not counted as regular error
        {
            frame.setMessage(createMessage("id:ns:testdoc:n=2:foo"));
            RoutingNode target = select();
            replyError(target, new com.yahoo.messagebus.Error(DocumentProtocol.ERROR_BUSY, "Get busy livin' or get busy resendin'"));
        }
        assertEquals(0, checker.recordedFailures); // BUSY not counted as error
        {
            frame.setMessage(createMessage("id:ns:testdoc:n=3:foo"));
            RoutingNode target = select();
            replyError(target, new com.yahoo.messagebus.Error(DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED, "oh no"));
        }
        assertEquals(0, checker.recordedFailures); // TaS failures not counted as error
    }

    @Test
    public void other_errors_during_feed_are_counted_as_errors_that_may_trigger_random_send() {
        setUpSingleNodeFixturesWithInitializedPolicy();
        var checker = policyFactory.getLastParameters().instabilityChecker;
        {
            frame.setMessage(createMessage("id:ns:testdoc:n=1:foo"));
            RoutingNode target = select();
            replyError(target, new com.yahoo.messagebus.Error(DocumentProtocol.ERROR_ABORTED, "shop's closing, go home"));
        }
        assertEquals(1, checker.recordedFailures);
    }

    // Left to test?

    // Cluster state down - Not overwrite last good nodes to send random to?
    //                      Overwrite cached state or not?
}
