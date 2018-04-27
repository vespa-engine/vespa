// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.network.Identity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen
 */
public class ServiceAddressTestCase {

    private Slobrok slobrok;
    private RPCNetwork network;

    @Before
    public void setUp() throws ListenFailedException, UnknownHostException {
        slobrok = new Slobrok();
        network = new RPCNetwork(new RPCNetworkParams()
                                 .setIdentity(new Identity("foo"))
                                 .setSlobrokConfigId("raw:slobrok[1]\nslobrok[0].connectionspec \"" +
                                                     new Spec("localhost", slobrok.port()).toString() + "\"\n"));
    }

    @After
    public void tearDown() {
        network.shutdown();
        slobrok.stop();
    }

    @Test
    public void testAddrServiceAddress() {
        assertNullAddress("tcp");
        assertNullAddress("tcp/");
        assertNullAddress("tcp/localhost");
        assertNullAddress("tcp/localhost:");
        assertNullAddress("tcp/localhost:1977");
        assertNullAddress("tcp/localhost:1977/");
        assertAddress("tcp/localhost:1977/session", "tcp/localhost:1977", "session");
        assertNullAddress("tcp/localhost:/session");
        //assertNullAddress("tcp/:1977/session");
        assertNullAddress("tcp/:/session");
    }

    @Test
    public void testNameServiceAddress() {
        network.unregisterSession("session");
        assertTrue(waitSlobrok("foo/session", 0));
        assertNullAddress("foo/session");

        network.registerSession("session");
        assertTrue(waitSlobrok("foo/session", 1));
        assertAddress("foo/session", network.getConnectionSpec(), "session");
    }

    private boolean waitSlobrok(String pattern, int num) {
        for (int i = 0; i < 1000 && !Thread.currentThread().isInterrupted(); ++i) {
            Mirror.Entry[] res = network.getMirror().lookup(pattern);
            if (res.length == num) {
                return true;
            }
            try {
                Thread.sleep(10);
            }
            catch (InterruptedException e) {
                // ignore
            }
        }
        return false;
    }

    private void assertNullAddress(String pattern) {
        assertNull(new RPCService(network.getMirror(), pattern).resolve());
    }

    private void assertAddress(String pattern, String expectedSpec, String expectedSession) {
        RPCService service = new RPCService(network.getMirror(), pattern);
        RPCServiceAddress obj = service.resolve();
        assertNotNull(obj);
        assertNotNull(obj.getConnectionSpec());
        assertEquals(expectedSpec, obj.getConnectionSpec().toString());
        if (expectedSession != null) {
            assertEquals(expectedSession, obj.getSessionName());
        }
    }

}
