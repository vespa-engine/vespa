// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.jrt.slobrok.api.Mirror;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class LoadBalancerTestCase {

    @Test
    public void requireThatParseExceptionIsReadable() {
        assertIllegalArgument("foo", "bar", "Expected recipient on the form 'foo/x/[y.]number/z', got 'bar'.");
        assertIllegalArgument("foo", "foobar", "Expected recipient on the form 'foo/x/[y.]number/z', got 'foobar'.");
        assertIllegalArgument("foo", "foo", "Expected recipient on the form 'foo/x/[y.]number/z', got 'foo'.");
        assertIllegalArgument("foo", "foo/", "Expected recipient on the form 'foo/x/[y.]number/z', got 'foo/'.");
        assertIllegalArgument("foo", "foo/0", "Expected recipient on the form 'foo/x/[y.]number/z', got 'foo/0'.");
        assertIllegalArgument("foo", "foo/0.", "Expected recipient on the form 'foo/x/[y.]number/z', got 'foo/0.'.");
        assertIllegalArgument("foo", "foo/0.bar", "Expected recipient on the form 'foo/x/[y.]number/z', got 'foo/0.bar'.");
        assertIllegalArgument("foo", "foo/bar", "Expected recipient on the form 'foo/x/[y.]number/z', got 'foo/bar'.");
        assertIllegalArgument("foo", "foo/bar.", "Expected recipient on the form 'foo/x/[y.]number/z', got 'foo/bar.'.");
        assertIllegalArgument("foo", "foo/bar.0", "Expected recipient on the form 'foo/x/[y.]number/z', got 'foo/bar.0'.");
    }

    private static void assertIllegalArgument(String clusterName, String recipient, String expectedMessage) {
        LegacyLoadBalancer policy = new LegacyLoadBalancer(clusterName);
        try {
            fail("Expected exception, got index " + policy.getIndex(recipient) + ".");
        } catch (IllegalArgumentException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    @Test
    public void testLoadBalancerCreation() {
        LoadBalancerPolicy lbp = new LoadBalancerPolicy("cluster=docproc/cluster.mobile.indexing;session=chain.mobile.indexing");
        assertTrue(lbp.getLoadBalancer() instanceof LegacyLoadBalancer);
        lbp = new LoadBalancerPolicy("cluster=docproc/cluster.mobile.indexing;session=chain.mobile.indexing;type=legacy");
        assertTrue(lbp.getLoadBalancer() instanceof LegacyLoadBalancer);
        lbp = new LoadBalancerPolicy("cluster=docproc/cluster.mobile.indexing;session=chain.mobile.indexing;type=adaptive");
        assertTrue(lbp.getLoadBalancer() instanceof AdaptiveLoadBalancer);
    }

    @Test
    public void testAdaptiveLoadBalancer() {
        LoadBalancer lb = new AdaptiveLoadBalancer("foo");

        List<Mirror.Entry> entries = Arrays.asList(new Mirror.Entry("foo/0/default", "tcp/bar:1"),
                new Mirror.Entry("foo/1/default", "tcp/bar:2"),
                new Mirror.Entry("foo/2/default", "tcp/bar:3"));
        List<LoadBalancer.NodeMetrics> weights = lb.getNodeWeights();

        for (int i = 0; i < 9999; i++) {
            LoadBalancer.Node node = lb.getRecipient(entries);
            assertNotNull(node);
        }

        long sentSum = 0;
        for (var metrics : weights) {
            assertTrue(10 > Math.abs(metrics.sent() - 3333));
            sentSum += metrics.sent();
        }
        assertEquals(9999, sentSum);

        for (var metrics : weights) {
            metrics.reset();
        }

        // Simulate 1/1, 1/2, 1/4 processing capacity
        for (int i = 0; i < 9999; i++) {
            LoadBalancer.Node node = lb.getRecipient(entries);
            assertNotNull(node);
            if (node.entry.getName().contains("1")) {
                lb.received(node, false);
            } else if (node.entry.getName().contains("2")) {
                if ((i % 2) == 0) {
                    lb.received(node, false);
                }
            } else {
                if ((i % 4) == 0) {
                    lb.received(node, false);
                }
            }
        }

        sentSum = 0;
        long sumPending = 0;
        for (var metrics : weights) {
            System.out.println("m: s=" + metrics.sent() + " p=" + metrics.pending());
            sentSum += metrics.sent();
            sumPending += metrics.pending();
        }
        assertEquals(9999, sentSum);
        assertTrue(200 > Math.abs(sumPending -  2700));
        assertTrue( 100 > Math.abs(weights.get(0).sent() - 1780));
        assertTrue( 200 > Math.abs(weights.get(1).sent() - 5500));
        assertTrue( 100 > Math.abs(weights.get(2).sent() - 2650));
        assertTrue( 100 > Math.abs(weights.get(0).pending() - 1340));
        assertEquals( 0, weights.get(1).pending());
        assertTrue( 100 > Math.abs(weights.get(2).pending() - 1340));
    }

    @Test
    public void testLegacyLoadBalancer() {
        LoadBalancer lb = new LegacyLoadBalancer("foo");

        List<Mirror.Entry> entries = Arrays.asList(new Mirror.Entry("foo/0/default", "tcp/bar:1"),
                                                   new Mirror.Entry("foo/1/default", "tcp/bar:2"),
                                                   new Mirror.Entry("foo/2/default", "tcp/bar:3"));
        List<LoadBalancer.NodeMetrics> weights = lb.getNodeWeights();

        {
            for (int i = 0; i < 99; i++) {
                LoadBalancer.Node node = lb.getRecipient(entries);
                assertEquals("foo/" + (i % 3) + "/default" , node.entry.getName());
            }

            assertEquals(33, weights.get(0).sent());
            assertEquals(33, weights.get(1).sent());
            assertEquals(33, weights.get(2).sent());

            weights.get(0).reset();
            weights.get(1).reset();
            weights.get(2).reset();
        }

        {
            // Simulate that one node is overloaded. It returns busy twice as often as the others.
            for (int i = 0; i < 100; i++) {
                lb.received(new LoadBalancer.Node(new Mirror.Entry("foo/0/default", "tcp/bar:1"), weights.get(0)), true);
                lb.received(new LoadBalancer.Node(new Mirror.Entry("foo/0/default", "tcp/bar:1"), weights.get(0)), false);
                lb.received(new LoadBalancer.Node(new Mirror.Entry("foo/0/default", "tcp/bar:1"), weights.get(0)), false);

                lb.received(new LoadBalancer.Node(new Mirror.Entry("foo/2/default", "tcp/bar:3"), weights.get(2)), true);
                lb.received(new LoadBalancer.Node(new Mirror.Entry("foo/2/default", "tcp/bar:3"), weights.get(2)), false);
                lb.received(new LoadBalancer.Node(new Mirror.Entry("foo/2/default", "tcp/bar:3"), weights.get(2)), false);

                lb.received(new LoadBalancer.Node(new Mirror.Entry("foo/1/default", "tcp/bar:2"), weights.get(1)), true);
                lb.received(new LoadBalancer.Node(new Mirror.Entry("foo/1/default", "tcp/bar:2"), weights.get(1)), true);
                lb.received(new LoadBalancer.Node(new Mirror.Entry("foo/1/default", "tcp/bar:2"), weights.get(1)), false);
            }

            assertEquals(421, (int)(100 * ((LegacyLoadBalancer.LegacyNodeMetrics)weights.get(0)).weight / ((LegacyLoadBalancer.LegacyNodeMetrics)weights.get(1)).weight));
            assertEquals(100, (int)(100 * ((LegacyLoadBalancer.LegacyNodeMetrics)weights.get(1)).weight));
            assertEquals(421, (int)(100 * ((LegacyLoadBalancer.LegacyNodeMetrics)weights.get(2)).weight / ((LegacyLoadBalancer.LegacyNodeMetrics)weights.get(1)).weight));
        }


        assertEquals("foo/0/default" , lb.getRecipient(entries).entry.getName());
        assertEquals("foo/0/default" , lb.getRecipient(entries).entry.getName());
        assertEquals("foo/1/default" , lb.getRecipient(entries).entry.getName());
        assertEquals("foo/2/default" , lb.getRecipient(entries).entry.getName());
        assertEquals("foo/2/default" , lb.getRecipient(entries).entry.getName());
        assertEquals("foo/2/default" , lb.getRecipient(entries).entry.getName());
        assertEquals("foo/2/default" , lb.getRecipient(entries).entry.getName());
        assertEquals("foo/0/default" , lb.getRecipient(entries).entry.getName());
        assertEquals("foo/0/default" , lb.getRecipient(entries).entry.getName());
        assertEquals("foo/0/default" , lb.getRecipient(entries).entry.getName());
    }

    private void verifyLoadBalancerOneItemOnly(LoadBalancer lb) {

        List<Mirror.Entry> entries = Arrays.asList(new Mirror.Entry("foo/0/default", "tcp/bar:1") );
        List<LoadBalancer.NodeMetrics> weights = lb.getNodeWeights();

        assertEquals("foo/0/default" , lb.getRecipient(entries).entry.getName());

        lb.received(new LoadBalancer.Node(new Mirror.Entry("foo/0/default", "tcp/bar:1"), weights.get(0)), true); // busy

        assertEquals("foo/0/default" , lb.getRecipient(entries).entry.getName());
    }
    @Test
    public void testLoadBalancerOneItemOnly() {
        verifyLoadBalancerOneItemOnly(new LegacyLoadBalancer("foo"));
        verifyLoadBalancerOneItemOnly(new AdaptiveLoadBalancer("foo"));
    }
}
