// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DetachTest {

    Test.Orb      server;
    Acceptor      acceptor;
    Test.Orb      client;
    Target        target;
    Test.Receptor receptor;
    Test.Barrier  barrier;

    @Before
    public void setUp() throws ListenFailedException {
        server   = new Test.Orb(new Transport());
        client   = new Test.Orb(new Transport());
        acceptor = server.listen(new Spec(Test.PORT));
        target   = client.connect(new Spec("localhost", Test.PORT));

        server.addMethod(new Method("d_inc", "i", "i", this,
                                    "rpc_detach_inc"));
        server.addMethod(new Method("d_inc_r", "i", "i", this,
                                    "rpc_detach_inc_return"));
        server.addMethod(new Method("inc_b", "i", "i", this,
                                    "rpc_inc_barrier"));
        receptor = new Test.Receptor();
        barrier = new Test.Barrier();
    }

    @After
    public void tearDown() {
        target.close();
        acceptor.shutdown().join();
        client.transport().shutdown().join();
        server.transport().shutdown().join();
    }

    Request detached = null;

    public void rpc_detach_inc(Request req) {
        req.detach();
        int value = req.parameters().get(0).asInt32();
        req.returnValues().add(new Int32Value(value + 1));
        detached = req;
    }

    public void rpc_detach_inc_return(Request req) {
        req.detach();
        int value = req.parameters().get(0).asInt32();
        req.returnValues().add(new Int32Value(value + 1));
        req.returnRequest();
    }

    public void rpc_inc_barrier(Request req) {
        int value = req.parameters().get(0).asInt32();
        req.returnValues().add(new Int32Value(value + 1));
        receptor.put(req);
        barrier.waitFor();
    }

    @org.junit.Test
    public void testDetach() {
        Test.Waiter w1 = new Test.Waiter();
        Request req1 = new Request("d_inc");
        req1.parameters().add(new Int32Value(50));
        target.invokeAsync(req1, 5.0, w1);

        Request req2 = new Request("d_inc_r");
        req2.parameters().add(new Int32Value(60));
        target.invokeSync(req2, 5.0);

        assertTrue(!req2.isError());
        assertEquals(1, req2.returnValues().size());
        assertEquals(61, req2.returnValues().get(0).asInt32());

        assertTrue(detached != null);
        assertTrue(!w1.isDone());
        assertTrue(server.checkReadCounts(2, 0, 0));
        assertTrue(server.checkWriteCounts(0, 1, 0));
        assertTrue(client.checkReadCounts(0, 1, 0));
        assertTrue(client.checkWriteCounts(2, 0, 0));
        assertTrue(server.readBytes == client.writeBytes);
        assertTrue(client.readBytes == server.writeBytes);

        detached.returnRequest();
        try {
            detached.returnRequest();
            assertTrue(false);
        } catch (IllegalStateException e) {}
        detached = null;
        w1.waitDone();

        assertTrue(!req1.isError());
        assertEquals(1, req1.returnValues().size());
        assertEquals(51, req1.returnValues().get(0).asInt32());
        assertTrue(server.checkReadCounts(2, 0, 0));
        assertTrue(server.checkWriteCounts(0, 2, 0));
        assertTrue(client.checkReadCounts(0, 2, 0));
        assertTrue(client.checkWriteCounts(2, 0, 0));
        assertTrue(server.readBytes == client.writeBytes);
        assertTrue(client.readBytes == server.writeBytes);
    }

    @org.junit.Test
    public void testBogusDetach() {
        Request req1 = new Request("inc_b");
        req1.parameters().add(new Int32Value(200));
        try {
            req1.detach();
            assertTrue(false);
        } catch (IllegalStateException e) {}

        Request req2 = new Request("inc_b");
        req2.parameters().add(new Int32Value(200));
        try {
            req2.returnRequest();
            assertTrue(false);
        } catch (IllegalStateException e) {}

        Test.Waiter w = new Test.Waiter();
        Request req3 = new Request("inc_b");
        req3.parameters().add(new Int32Value(100));
        target.invokeAsync(req3, 5.0, w);
        Request blocked = (Request) receptor.get();
        try {
            blocked.returnRequest();
            assertTrue(false);
        } catch (IllegalStateException e) {}
        barrier.breakIt();
        w.waitDone();
        assertTrue(!req3.isError());
        assertEquals(1, req3.returnValues().size());
        assertEquals(101, req3.returnValues().get(0).asInt32());
    }

}
