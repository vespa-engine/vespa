// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InvokeErrorTest {

    final double timeout=60.0;
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
        server.addMethod(new Method("test", "iib", "i", this, "rpc_test"));
        server.addMethod(new Method("test_barrier", "iib", "i", this,
                                    "rpc_test_barrier"));
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
        int value = req.parameters().get(0).asInt32();
        int error = req.parameters().get(1).asInt32();
        int extra = req.parameters().get(2).asInt8();

        req.returnValues().add(new Int32Value(value));
        if (extra != 0) {
            req.returnValues().add(new Int32Value(value));
        }
        if (error != 0) {
            req.setError(error, "Custom error");
        }
    }

    public void rpc_test_barrier(Request req) {
        rpc_test(req);
        barrier.waitFor();
    }

    @org.junit.Test
    public void testNoError() {
        Request req1 = new Request("test");
        req1.parameters().add(new Int32Value(42));
        req1.parameters().add(new Int32Value(0));
        req1.parameters().add(new Int8Value((byte)0));
        target.invokeSync(req1, timeout);
        assertTrue(!req1.isError());
        assertEquals(1, req1.returnValues().size());
        assertEquals(42, req1.returnValues().get(0).asInt32());
    }

    @org.junit.Test
    public void testNoSuchMethod() {
        Request req1 = new Request("bogus");
        target.invokeSync(req1, timeout);
        assertTrue(req1.isError());
        assertEquals(0, req1.returnValues().size());
        assertEquals(ErrorCode.NO_SUCH_METHOD, req1.errorCode());
    }

    @org.junit.Test
    public void testWrongParameters() {
        Request req1 = new Request("test");
        req1.parameters().add(new Int32Value(42));
        req1.parameters().add(new Int32Value(0));
        req1.parameters().add(new Int32Value(0));
        target.invokeSync(req1, timeout);
        assertTrue(req1.isError());
        assertEquals(0, req1.returnValues().size());
        assertEquals(ErrorCode.WRONG_PARAMS, req1.errorCode());

        Request req2 = new Request("test");
        req2.parameters().add(new Int32Value(42));
        req2.parameters().add(new Int32Value(0));
        target.invokeSync(req2, timeout);
        assertTrue(req2.isError());
        assertEquals(0, req2.returnValues().size());
        assertEquals(ErrorCode.WRONG_PARAMS, req2.errorCode());

        Request req3 = new Request("test");
        req3.parameters().add(new Int32Value(42));
        req3.parameters().add(new Int32Value(0));
        req3.parameters().add(new Int8Value((byte)0));
        req3.parameters().add(new Int8Value((byte)0));
        target.invokeSync(req3, timeout);
        assertTrue(req3.isError());
        assertEquals(0, req3.returnValues().size());
        assertEquals(ErrorCode.WRONG_PARAMS, req3.errorCode());
    }

    @org.junit.Test
    public void testWrongReturnValues() {
        Request req1 = new Request("test");
        req1.parameters().add(new Int32Value(42));
        req1.parameters().add(new Int32Value(0));
        req1.parameters().add(new Int8Value((byte)1));
        target.invokeSync(req1, timeout);
        assertTrue(req1.isError());
        assertEquals(0, req1.returnValues().size());
        assertEquals(ErrorCode.WRONG_RETURN, req1.errorCode());
    }

    @org.junit.Test
    public void testMethodFailed() {
        Request req1 = new Request("test");
        req1.parameters().add(new Int32Value(42));
        req1.parameters().add(new Int32Value(75000));
        req1.parameters().add(new Int8Value((byte)0));
        target.invokeSync(req1, timeout);
        assertTrue(req1.isError());
        assertEquals(0, req1.returnValues().size());
        assertEquals(75000, req1.errorCode());

        Request req2 = new Request("test");
        req2.parameters().add(new Int32Value(42));
        req2.parameters().add(new Int32Value(75000));
        req2.parameters().add(new Int8Value((byte)1));
        target.invokeSync(req2, timeout);
        assertTrue(req2.isError());
        assertEquals(0, req2.returnValues().size());
        assertEquals(75000, req2.errorCode());
    }

    @org.junit.Test
    public void testConnectionError() {
        Test.Waiter w = new Test.Waiter();
        Request req1 = new Request("test_barrier");
        req1.parameters().add(new Int32Value(42));
        req1.parameters().add(new Int32Value(0));
        req1.parameters().add(new Int8Value((byte)0));
        target.invokeAsync(req1, timeout, w);
        target.close();
        client.transport().sync();
        barrier.breakIt();
        w.waitDone();
        assertTrue(!target.isValid());
        assertTrue(req1.isError());
        assertEquals(0, req1.returnValues().size());
        assertEquals(ErrorCode.CONNECTION, req1.errorCode());
    }

}
