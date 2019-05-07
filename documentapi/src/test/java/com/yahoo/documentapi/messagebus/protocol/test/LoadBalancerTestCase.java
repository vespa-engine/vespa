// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test;

import com.yahoo.documentapi.messagebus.protocol.LoadBalancer;
import com.yahoo.jrt.slobrok.api.Mirror;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
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
        LoadBalancer policy = new LoadBalancer(clusterName);
        try {
            fail("Expected exception, got index " + policy.getIndex(recipient) + ".");
        } catch (IllegalArgumentException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    @Test
    public void testLoadBalancer() {
        LoadBalancer lb = new LoadBalancer("foo");

        List<Mirror.Entry> entries = Arrays.asList(new Mirror.Entry("foo/0/default", "tcp/bar:1"),
                                                   new Mirror.Entry("foo/1/default", "tcp/bar:2"),
                                                   new Mirror.Entry("foo/2/default", "tcp/bar:3"));
        List<LoadBalancer.NodeMetrics> weights = lb.getNodeWeights();

        {
            for (int i = 0; i < 99; i++) {
                LoadBalancer.Node node = lb.getRecipient(entries);
                assertEquals("foo/" + (i % 3) + "/default" , node.entry.getName());
            }

            assertEquals(33, weights.get(0).sent.intValue());
            assertEquals(33, weights.get(1).sent.intValue());
            assertEquals(33, weights.get(2).sent.intValue());

            weights.get(0).sent.set(0);
            weights.get(1).sent.set(0);
            weights.get(2).sent.set(0);
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

            assertEquals(421, (int)(100 * weights.get(0).weight / weights.get(1).weight));
            assertEquals(100, (int)(100 * weights.get(1).weight));
            assertEquals(421, (int)(100 * weights.get(2).weight / weights.get(1).weight));
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

    @Test
    public void testLoadBalancerOneItemOnly() {
        LoadBalancer lb = new LoadBalancer("foo");

        List<Mirror.Entry> entries = Arrays.asList(new Mirror.Entry("foo/0/default", "tcp/bar:1") );
        List<LoadBalancer.NodeMetrics> weights = lb.getNodeWeights();

        assertEquals("foo/0/default" , lb.getRecipient(entries).entry.getName());

        lb.received(new LoadBalancer.Node(new Mirror.Entry("foo/0/default", "tcp/bar:1"), weights.get(0)), true); // busy

        assertEquals("foo/0/default" , lb.getRecipient(entries).entry.getName());

    }
}
