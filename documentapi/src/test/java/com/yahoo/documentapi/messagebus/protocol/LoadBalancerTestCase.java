// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.jrt.slobrok.api.Mirror;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

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
        LoadBalancer policy = new AdaptiveLoadBalancer(clusterName);
        try {
            fail("Expected exception, got index " + policy.getIndex(recipient) + ".");
        } catch (IllegalArgumentException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    @Test
    public void testLoadBalancerCreation() {
        LoadBalancerPolicy lbp = new LoadBalancerPolicy("cluster=docproc/cluster.mobile.indexing;session=chain.mobile.indexing");
        assertTrue(lbp.getLoadBalancer() instanceof AdaptiveLoadBalancer);
        lbp = new LoadBalancerPolicy("cluster=docproc/cluster.mobile.indexing;session=chain.mobile.indexing;type=legacy");
        assertTrue(lbp.getLoadBalancer() instanceof AdaptiveLoadBalancer);
        lbp = new LoadBalancerPolicy("cluster=docproc/cluster.mobile.indexing;session=chain.mobile.indexing;type=adaptive");
        assertTrue(lbp.getLoadBalancer() instanceof AdaptiveLoadBalancer);
    }

    @Test
    public void testAdaptiveLoadBalancer() {
        LoadBalancer lb = new AdaptiveLoadBalancer("foo", new Random(1));

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
        assertEquals(2039, sumPending);
        assertEquals(1332, weights.get(0).sent());
        assertEquals(6645, weights.get(1).sent());
        assertEquals(2022, weights.get(2).sent());
        assertEquals(1020, weights.get(0).pending());
        assertEquals(0, weights.get(1).pending());
        assertEquals(1019, weights.get(2).pending());
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
        verifyLoadBalancerOneItemOnly(new AdaptiveLoadBalancer("foo"));
    }
}
