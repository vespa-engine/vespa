// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AbortTest {

    Supervisor   server;
    Acceptor     acceptor;
    Supervisor   client;
    Target       target;
    Test.Barrier barrier;

    @Before
    public void setUp() throws ListenFailedException {
        server   = new Supervisor(new Transport());
        client   = new Supervisor(new Transport());
        acceptor = server.listen(new Spec(Test.PORT));
        target   = client.connect(new Spec("localhost", Test.PORT));
        server.addMethod(new Method("test", "i", "i", this, "rpc_test"));
        barrier = new Test.Barrier();
    }

    @After
    public void tearDown() {
        target.close();
        acceptor.shutdown().join();
        client.transport().shutdown().join();
        server.transport().shutdown().join();
    }

    public void rpc_test(Request req) {
        barrier.waitFor();
        int value = req.parameters().get(0).asInt32();
        req.returnValues().add(new Int32Value(value));
    }

    @org.junit.Test
    public void testAbort() {
        Test.Waiter w = new Test.Waiter();
        Request req = new Request("test");
        req.parameters().add(new Int32Value(20));
        target.invokeAsync(req, 5.0, w);
        req.abort();
        barrier.breakIt();
        w.waitDone();
        assertTrue(req.isError());
        assertEquals(ErrorCode.ABORT, req.errorCode());
        assertEquals(0, req.returnValues().size());

        Request req2 = new Request("test");
        req2.parameters().add(new Int32Value(30));
        target.invokeSync(req2, 5.0);
        assertTrue(!req2.isError());
        assertEquals(1, req2.returnValues().size());
        assertEquals(30, req2.returnValues().get(0).asInt32());

        req2.abort();
        assertTrue(!req2.isError());
        assertEquals(1, req2.returnValues().size());
        assertEquals(30, req2.returnValues().get(0).asInt32());

        assertTrue(req.isError());
        assertEquals(ErrorCode.ABORT, req.errorCode());
        assertEquals(0, req.returnValues().size());
    }

    @org.junit.Test
    public void testBogusAbort() {
        Request req = new Request("test");
        req.parameters().add(new Int32Value(40));
        try {
            req.abort();
            assertTrue(false);
        } catch (IllegalStateException e) {}
    }

}
