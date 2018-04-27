// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InvokeVoidTest {

    Test.Orb server;
    Acceptor acceptor;
    Test.Orb client;
    Target   target;

    @Before
    public void setUp() throws ListenFailedException {
        server   = new Test.Orb(new Transport());
        client   = new Test.Orb(new Transport());
        acceptor = server.listen(new Spec(Test.PORT));
        target   = client.connect(new Spec("localhost", Test.PORT));

        server.addMethod(new Method("set", "i", "", this, "rpc_set")
                         .methodDesc("Set the stored value")
                         .paramDesc(0, "value", "the new value"));
        server.addMethod(new Method("inc", "", "", this, "rpc_inc")
                         .methodDesc("Increase the stored value"));
        server.addMethod(new Method("get", "", "i", this, "rpc_get")
                         .methodDesc("Get the stored value")
                         .returnDesc(0, "value", "the stored value"));
    }

    @After
    public void tearDown() {
        target.close();
        acceptor.shutdown().join();
        client.transport().shutdown().join();
        server.transport().shutdown().join();
    }

    private int value = 0;

    public void rpc_set(Request req) {
        value = req.parameters().get(0).asInt32();
    }
    public void rpc_inc(Request req) {
        value++;
    }
    public void rpc_get(Request req) {
        req.returnValues().add(new Int32Value(value));
    }

    @org.junit.Test
    public void testInvokeVoid() {
        Request req = new Request("set");
        req.parameters().add(new Int32Value(40));
        target.invokeSync(req, 5.0);
        assertTrue(!req.isError());
        assertEquals(0, req.returnValues().size());

        target.invokeVoid(new Request("inc"));
        target.invokeVoid(new Request("inc"));

        req = new Request("get");
        target.invokeSync(req, 5.0);
        assertTrue(!req.isError());
        assertEquals(42, req.returnValues().get(0).asInt32());

        assertTrue(server.checkReadCounts(4, 0, 0));
        assertTrue(server.checkWriteCounts(0, 2, 0));
        assertTrue(client.checkReadCounts(0, 2, 0));
        assertTrue(client.checkWriteCounts(4, 0, 0));
        assertTrue(server.readBytes == client.writeBytes);
        assertTrue(client.readBytes == server.writeBytes);
    }

}
