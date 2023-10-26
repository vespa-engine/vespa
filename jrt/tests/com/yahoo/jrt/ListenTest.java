// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ListenTest {

    Supervisor server;

    @Before
    public void setUp() {
        server = new Supervisor(new Transport());
    }

    @After
    public void tearDown() {
        server.transport().shutdown().join();
    }

    @org.junit.Test
    public void testListen() {
        try {
            Acceptor a = server.listen(new Spec(0));
            assertTrue(a.port() > 0);
            a.shutdown().join();
            assertEquals(-1, a.port());
        } catch (ListenFailedException e) {
            assertTrue(false);
        }
        try {
            Acceptor a = server.listen(new Spec(null, 0));
            assertTrue(a.port() > 0);
            a.shutdown().join();
            assertEquals(-1, a.port());
        } catch (ListenFailedException e) {
            assertTrue(false);
        }
        try {
            Acceptor a = server.listen(new Spec("tcp/" + 0));
            assertTrue(a.port() > 0);
            a.shutdown().join();
            assertEquals(-1, a.port());
        } catch (ListenFailedException e) {
            assertTrue(false);
        }
        try {
            Acceptor a = server.listen(new Spec(0));
            Acceptor b = server.listen(new Spec(0));
            Acceptor c = server.listen(new Spec(0));
            assertTrue(a.port() > 0);
            assertTrue(b.port() > 0);
            assertTrue(c.port() > 0);
            assertTrue(a.port() != b.port());
            assertTrue(a.port() != c.port());
            assertTrue(b.port() != c.port());
            a.shutdown().join();
            assertEquals(-1, a.port());
            b.shutdown().join();
            assertEquals(-1, b.port());
            c.shutdown().join();
            assertEquals(-1, c.port());
        } catch (ListenFailedException e) {
            assertTrue(false);
        }
    }

    @org.junit.Test
    public void testBogusListen() {
        try {
            Acceptor a = server.listen(new Spec("bogus"));
            assertTrue(false);
        } catch (ListenFailedException e) {}
    }

    @org.junit.Test
    public void testListenSamePort() {
        try {
            Acceptor a = server.listen(new Spec("tcp/0"));
            assertTrue(a.port() > 0);
            try {
                Acceptor b = server.listen(new Spec(a.port()));
                assertTrue(false);
            } catch (ListenFailedException e) {}
            a.shutdown().join();
            assertEquals(-1, a.port());
        } catch (ListenFailedException e) {
            assertTrue(false);
        }
    }
}
