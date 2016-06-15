// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


public class ListenTest extends junit.framework.TestCase {

    Supervisor server;

    public ListenTest(String name) {
        super(name);
    }

    public void setUp() {
        server = new Supervisor(new Transport());
    }

    public void tearDown() {
        server.transport().shutdown().join();
    }

    public void testListen() {
        try {
            Acceptor a = server.listen(new Spec(Test.PORT));
            assertEquals(Test.PORT, a.port());
            a.shutdown().join();
            assertEquals(-1, a.port());
        } catch (ListenFailedException e) {
            assertTrue(false);
        }
        try {
            Acceptor a = server.listen(new Spec(null, Test.PORT));
            assertEquals(Test.PORT, a.port());
            a.shutdown().join();
            assertEquals(-1, a.port());
        } catch (ListenFailedException e) {
            assertTrue(false);
        }
        try {
            Acceptor a = server.listen(new Spec("tcp/" + Test.PORT));
            assertEquals(Test.PORT, a.port());
            a.shutdown().join();
            assertEquals(-1, a.port());
        } catch (ListenFailedException e) {
            assertTrue(false);
        }
        try {
            Acceptor a = server.listen(new Spec(Test.PORT_0));
            Acceptor b = server.listen(new Spec(Test.PORT_1));
            Acceptor c = server.listen(new Spec(Test.PORT_2));
            assertEquals(Test.PORT_0, a.port());
            assertEquals(Test.PORT_1, b.port());
            assertEquals(Test.PORT_2, c.port());
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

    public void testBogusListen() {
        try {
            Acceptor a = server.listen(new Spec("bogus"));
            assertTrue(false);
        } catch (ListenFailedException e) {}

        try {
            Acceptor a = server.listen(new Spec(Test.PORT));
            assertEquals(Test.PORT, a.port());
            // try {
            //  Acceptor b = server.listen(new Spec(Test.PORT));
            //  assertTrue(false);
            // } catch (ListenFailedException e) {}
            a.shutdown().join();
            assertEquals(-1, a.port());
        } catch (ListenFailedException e) {
            assertTrue(false);
        }
    }

    public void testListenAnyPort() {
        try {
            Acceptor a = server.listen(new Spec("tcp/0"));
            assertTrue(a.port() > 0);
            // try {
            //  Acceptor b = server.listen(new Spec(a.port()));
            //  assertTrue(false);
            // } catch (ListenFailedException e) {}
            a.shutdown().join();
            assertEquals(-1, a.port());
        } catch (ListenFailedException e) {
            assertTrue(false);
        }
    }
}
