// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.concurrent.Timer;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Simon Thoresen
 */
public class TargetPoolTestCase {

    private Slobrok slobrok;
    private List<TestServer> servers;
    private Supervisor orb;

    @Before
    public void setUp() throws ListenFailedException {
        slobrok = new Slobrok();
        servers = new ArrayList<>();
        orb = new Supervisor(new Transport());
    }

    @After
    public void tearDown() {
        slobrok.stop();
        for (TestServer server : servers) {
            server.destroy();
        }
        orb.transport().shutdown().join();
    }

    @Test
    public void testConnectionExpire() throws ListenFailedException, UnknownHostException {
        // Necessary setup to be able to resolve targets.
        RPCServiceAddress adr1 = registerServer();
        RPCServiceAddress adr2 = registerServer();
        RPCServiceAddress adr3 = registerServer();

        PoolTimer timer = new PoolTimer();
        RPCTargetPool pool = new RPCTargetPool(timer, 0.666);

        // Assert that all connections expire.
        RPCTarget target;
        assertNotNull(target = pool.getTarget(orb, adr1)); target.subRef();
        assertNotNull(target = pool.getTarget(orb, adr2)); target.subRef();
        assertNotNull(target = pool.getTarget(orb, adr3)); target.subRef();
        assertEquals(3, pool.size());
        for (int i = 0; i < 10; ++i) {
            pool.flushTargets(false);
            assertEquals(3, pool.size());
        }
        timer.millis += 999;
        pool.flushTargets(false);
        assertEquals(0, pool.size());

        // Assert that only idle connections expire.
        assertNotNull(target = pool.getTarget(orb, adr1)); target.subRef();
        assertNotNull(target = pool.getTarget(orb, adr2)); target.subRef();
        assertNotNull(target = pool.getTarget(orb, adr3)); target.subRef();
        assertEquals(3, pool.size());
        timer.millis += 444;
        pool.flushTargets(false);
        assertEquals(3, pool.size());
        assertNotNull(target = pool.getTarget(orb, adr2)); target.subRef();
        assertNotNull(target = pool.getTarget(orb, adr3)); target.subRef();
        assertEquals(3, pool.size());
        timer.millis += 444;
        pool.flushTargets(false);
        assertEquals(2, pool.size());
        assertNotNull(target = pool.getTarget(orb, adr3)); target.subRef();
        timer.millis += 444;
        pool.flushTargets(false);
        assertEquals(1, pool.size());
        timer.millis += 444;
        pool.flushTargets(false);
        assertEquals(0, pool.size());

        // Assert that connections never expire while they are referenced.
        assertNotNull(target = pool.getTarget(orb, adr1));
        assertEquals(1, pool.size());
        for (int i = 0; i < 10; ++i) {
            timer.millis += 999;
            pool.flushTargets(false);
            assertEquals(1, pool.size());
        }
        target.subRef();
        timer.millis += 999;
        pool.flushTargets(false);
        assertEquals(0, pool.size());
    }

    private RPCServiceAddress registerServer() {
        servers.add(new TestServer("srv" + servers.size(), null, slobrok, null));
        return new RPCServiceAddress("foo/bar", servers.get(servers.size() - 1).mb.getConnectionSpec());
    }

    private static class PoolTimer implements Timer {
        long millis = 0;

        @Override
        public long milliTime() {
            return millis;
        }
    }

}
