// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import org.junit.After;
import org.junit.Before;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TimeoutTest {

    Supervisor   server;
    Acceptor     acceptor;
    Supervisor   client;
    Target       target;
    Test.Barrier barrier;

    @Before
    public void setUp() throws ListenFailedException {
        server   = new Supervisor(new Transport());
        client   = new Supervisor(new Transport());
        acceptor = server.listen(new Spec(0));
        target   = client.connect(new Spec("localhost", acceptor.port()));
        server.addMethod(new Method("concat", "ss", "s", this::rpc_concat)
                         .methodDesc("Concatenate 2 strings")
                         .paramDesc(0, "str1", "a string")
                         .paramDesc(1, "str2", "another string")
                         .returnDesc(0, "ret", "str1 followed by str2"));
        barrier = new Test.Barrier();
    }

    @After
    public void tearDown() {
        target.close();
        acceptor.shutdown().join();
        client.transport().shutdown().join();
        server.transport().shutdown().join();
    }

    private void rpc_concat(Request req) {
        barrier.waitFor();
        req.returnValues().add(new StringValue(req.parameters()
                                               .get(0).asString() +
                                               req.parameters()
                                               .get(1).asString()));
    }

    @org.junit.Test
    public void testTimeout() {
        Request req = new Request("concat");
        req.parameters().add(new StringValue("abc"));
        req.parameters().add(new StringValue("def"));

        target.invokeSync(req, Duration.ofMillis(100));
        barrier.breakIt();

        Request flush = new Request("frt.rpc.ping");
        target.invokeSync(flush, Duration.ofSeconds(5));
        assertTrue(!flush.isError());

        assertTrue(req.isError());
        assertEquals(ErrorCode.TIMEOUT, req.errorCode());
        assertEquals("Request timed out after 0.1 seconds.", req.errorMessage());
        assertEquals(0, req.returnValues().size());
    }

    @org.junit.Test
    public void testNotTimeout() {
        Request req = new Request("concat");
        req.parameters().add(new StringValue("abc"));
        req.parameters().add(new StringValue("def"));

        Test.Waiter w = new Test.Waiter();
        target.invokeAsync(req, Duration.ofSeconds(30), w);
        try { Thread.sleep(2500); } catch (InterruptedException e) {}
        barrier.breakIt();
        w.waitDone();

        assertTrue(!req.isError());
        assertEquals(1, req.returnValues().size());
        assertEquals("abcdef", req.returnValues().get(0).asString());
    }

}
