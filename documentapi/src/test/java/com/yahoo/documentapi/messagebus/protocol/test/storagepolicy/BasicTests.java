// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test.storagepolicy;

import com.yahoo.collections.Pair;
import com.yahoo.documentapi.messagebus.protocol.WrongDistributionReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.routing.RoutingNode;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class BasicTests extends ContentPolicyTestEnvironment {

    /** Test that we can send a message through the policy. */
    @Test
    public void testNormalUsage() {
        setClusterNodes(new int[]{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 });
        // First we want a wrong distribution reply, so make sure we don't try correct node on random
        policyFactory.avoidPickingAtRandom(bucketOneNodePreference[0]);
        RoutingNode target = select();
        replyWrongDistribution(target, "foo", 5, "version:1 bits:16 distributor:10 storage:10");
        // Then send to correct node and verify that
        sendToCorrectNode("foo", bucketOneNodePreference[0]);
    }

    /** Test that we can identify newest cluster state and hang on to correct one. */
    @Test
    public void testRepliesWrongOrderDuringStateChange() throws Exception{
        {
            setClusterNodes(new int[]{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 });
            RoutingNode target1 = select();
            RoutingNode target2 = select();
            replyWrongDistribution(target2, "foo", 0, "version:2 bits:16 distributor:10");
            replyWrongDistribution(target1, "foo", 5, "version:1 bits:16 distributor:10 ." + bucketOneNodePreference[0] + ".s:d");
            sendToCorrectNode("foo", bucketOneNodePreference[0]);
        }
        tearDown();
        setUp();
        {
            setClusterNodes(new int[]{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 });
            RoutingNode target1 = select();
            RoutingNode target2 = select();
            replyWrongDistribution(target2, "foo", 0, "version:1 bits:16 distributor:10");
            replyWrongDistribution(target1, "foo", 5, "version:2 bits:16 distributor:10 ." + bucketOneNodePreference[0] + ".s:d");
            sendToCorrectNode("foo", bucketOneNodePreference[1]);
        }
    }
    /**
     * To be independent of changes in distribution algorithm, we programmatically calculate preferred order of
     * bucket 1, which we will be using in the tests. To avoid doing this ahead of every test, we still hardcode the
     * values, though only one place, and have this test to verify they are correct, and make it easy to update the values.
     */
    @Test
    public void testVerifyBucketOneNodePreferenceInTenNodeDefaultCluster() {
        int result[] = new int[10];
        setClusterNodes(new int[]{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 });
        String clusterState = " bits:16 storage:10 distributor:10";
        for (int i=0; i<10; ++i) {
            // Update cached cluster state, to reflect which node we want to find
            RoutingNode target = select();
            target.handleReply(new WrongDistributionReply("version:" + (i + 1) + clusterState));
            Reply reply = frame.getReceptor().getReply(60);
            assertNotNull(reply);
            assertFalse(reply.hasErrors());
            // Find correct target
            target = select();
            Pair<String, Integer> address = getAddress(target);
            result[i] = address.getSecond();
            removeNode(address.getSecond());
            clusterState += " ." + result[i] + ".s:d";
        }
        assertEquals(Arrays.toString(bucketOneNodePreference), Arrays.toString(result));
    }

    private void letPolicyCacheStateWithNoAvailableDistributors() {
        setClusterNodes(new int[]{ 0 });
        String clusterState = " bits:16 storage:1"; // No distributors online
        var target = select();
        // Update to cluster state with no distributors online
        replyWrongDistribution(target, "foo", null, clusterState);
    }

    @Test
    public void random_node_target_used_if_no_distributors_available_in_cached_state() {
        letPolicyCacheStateWithNoAvailableDistributors();
        select(); // Will trigger failure if no target is set, i.e. if an exception is thrown
    }

    @Test
    public void policy_context_set_on_no_distributors_exception_during_select() {
        letPolicyCacheStateWithNoAvailableDistributors();
        // Re-select with no distributors. Should still get a target selection context.
        var target = select();
        // Trigger a reply merge with error, which will try to inspect the context.
        // If an exception is thrown internally from a missing context, the reply will not be
        // set correctly and this will fail.
        replyError(target, new Error(ErrorCode.FATAL_ERROR, "oh no!"));
    }

}
