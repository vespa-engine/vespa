// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen
 */
public class ServicePoolTestCase {

    @Test
    public void testMaxSize() throws ListenFailedException {
        Slobrok slobrok = new Slobrok();
        RPCNetwork net = new RPCNetwork(new RPCNetworkParams().setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        RPCServicePool pool = new RPCServicePool(net, 2);

        pool.resolve("foo");
        assertEquals(1, pool.getSize());
        assertTrue(pool.hasService("foo"));
        assertTrue(!pool.hasService("bar"));
        assertTrue(!pool.hasService("baz"));

        pool.resolve("foo");
        assertEquals(1, pool.getSize());
        assertTrue(pool.hasService("foo"));
        assertTrue(!pool.hasService("bar"));
        assertTrue(!pool.hasService("baz"));

        pool.resolve("bar");
        assertEquals(2, pool.getSize());
        assertTrue(pool.hasService("foo"));
        assertTrue(pool.hasService("bar"));
        assertTrue(!pool.hasService("baz"));

        pool.resolve("baz");
        assertEquals(2, pool.getSize());
        assertTrue(!pool.hasService("foo"));
        assertTrue(pool.hasService("bar"));
        assertTrue(pool.hasService("baz"));

        pool.resolve("bar");
        assertEquals(2, pool.getSize());
        assertTrue(!pool.hasService("foo"));
        assertTrue(pool.hasService("bar"));
        assertTrue(pool.hasService("baz"));

        pool.resolve("foo");
        assertEquals(2, pool.getSize());
        assertTrue(pool.hasService("foo"));
        assertTrue(pool.hasService("bar"));
        assertTrue(!pool.hasService("baz"));

        slobrok.stop();
    }

}
