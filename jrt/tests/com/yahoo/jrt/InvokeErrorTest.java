// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import org.junit.After;
import org.junit.Before;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InvokeErrorTest {

    final Duration timeout = Duration.ofSeconds(60);
    Supervisor   server;
    Acceptor     acceptor;
    Supervisor   client;
    Target       target;
    Test.Barrier barrier;
    SimpleRequestAccessFilter filter;
    RpcTestMethod             testMethod;

    @Before
    public void setUp() throws ListenFailedException {
        server   = new Supervisor(new Transport());
        client   = new Supervisor(new Transport());
        acceptor = server.listen(new Spec(0));
        target   = client.connect(new Spec("localhost", acceptor.port()));
        filter = new SimpleRequestAccessFilter();
        testMethod = new RpcTestMethod();
        server.addMethod(new Method("test", "iib", "i", testMethod).requestAccessFilter(filter));
        server.addMethod(new Method("test_barrier", "iib", "i", this::rpc_test_barrier));
        barrier = new Test.Barrier();
    }

    @After
    public void tearDown() {
        target.close();
        acceptor.shutdown().join();
        client.transport().shutdown().join();
        server.transport().shutdown().join();
    }

    private void rpc_test_barrier(Request req) {
        testMethod.invoke(req);
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

    @org.junit.Test
    public void testFilterFailsRequest() {
        Request r = new Request("test");
        r.parameters().add(new Int32Value(42));
        r.parameters().add(new Int32Value(0));
        r.parameters().add(new Int8Value((byte)0));
        filter.allowed = false;
        assertFalse(filter.invoked);
        target.invokeSync(r, timeout);
        assertTrue(r.isError());
        assertTrue(filter.invoked);
        assertFalse(testMethod.invoked);
        assertEquals(ErrorCode.PERMISSION_DENIED, r.errorCode());
        assertEquals("Permission denied", r.errorMessage());
    }

    private static class RpcTestMethod implements MethodHandler {
        boolean invoked = false;

        @Override public void invoke(Request req) { invoked = true; rpc_test(req); }

        void rpc_test(Request req) {
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
    }

}
