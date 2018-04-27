// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WatcherTest {

    private static class Watcher implements TargetWatcher {
        private int notifyCnt = 0;
        public void notifyTargetInvalid(Target target) {
            notifyCnt++;
        }
        public int cnt() {
            return notifyCnt;
        }
        public boolean equals(Object rhs) {
            return true;
        }
        public int hashCode() {
            return 0;
        }
    }

    Supervisor server;
    Acceptor   acceptor;
    Supervisor client;
    Target     target;

    @Before
    public void setUp() throws ListenFailedException {
        server   = new Supervisor(new Transport());
        client   = new Supervisor(new Transport());
        acceptor = server.listen(new Spec(Test.PORT));
        target   = client.connect(new Spec("localhost", Test.PORT));
    }

    @After
    public void tearDown() {
        target.close();
        acceptor.shutdown().join();
        client.transport().shutdown().join();
        server.transport().shutdown().join();
    }

    @org.junit.Test
    public void testNotify() {
        Watcher w1 = new Watcher();
        Watcher w2 = new Watcher();
        Watcher w3 = new Watcher();
        Watcher w4 = new Watcher();
        Watcher w5 = new Watcher();

        assertTrue(target.addWatcher(w1));
        assertTrue(target.addWatcher(w1));
        assertTrue(target.addWatcher(w1));

        assertTrue(target.addWatcher(w2));
        assertTrue(target.addWatcher(w2));
        assertTrue(target.addWatcher(w2));
        assertTrue(target.removeWatcher(w2));
        assertTrue(target.removeWatcher(w2));
        assertTrue(target.addWatcher(w2));

        assertTrue(target.addWatcher(w3));
        assertTrue(target.removeWatcher(w3));

        assertTrue(target.removeWatcher(w4));

        assertTrue(target.addWatcher(w5));
        assertTrue(target.addWatcher(w5));
        assertTrue(target.addWatcher(w5));
        assertTrue(target.removeWatcher(w5));

        target.close();
        client.transport().sync();

        assertEquals(1, w1.cnt());
        assertEquals(1, w2.cnt());
        assertEquals(0, w3.cnt());
        assertEquals(0, w4.cnt());
        assertEquals(0, w5.cnt());

        assertFalse(target.removeWatcher(w1));
        assertFalse(target.removeWatcher(w2));
        assertFalse(target.addWatcher(w3));
        assertFalse(target.addWatcher(w4));
        assertFalse(target.addWatcher(w5));

        target.close();
        client.transport().sync();

        assertEquals(1, w1.cnt());
        assertEquals(1, w2.cnt());
        assertEquals(0, w3.cnt());
        assertEquals(0, w4.cnt());
        assertEquals(0, w5.cnt());
    }

}
