// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import org.junit.After;
import org.junit.Before;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InvokeAsyncTest {

    Supervisor   server;
    Acceptor     acceptor;
    Supervisor   client;
    Target       target;
    Test.Barrier barrier;
    SimpleRequestAccessFilter filter;

    @Before
    public void setUp() throws ListenFailedException {
        server   = new Supervisor(new Transport());
        client   = new Supervisor(new Transport());
        acceptor = server.listen(new Spec(0));
        target   = client.connect(new Spec("localhost", acceptor.port()));
        filter = new SimpleRequestAccessFilter();
        server.addMethod(new Method("concat", "ss", "s", this::rpc_concat)
                         .methodDesc("Concatenate 2 strings")
                         .paramDesc(0, "str1", "a string")
                         .paramDesc(1, "str2", "another string")
                         .returnDesc(0, "ret", "str1 followed by str2")
                         .requestAccessFilter(filter));
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
    public void testAsync() {
        Request req = new Request("concat");
        req.parameters().add(new StringValue("abc"));
        req.parameters().add(new StringValue("def"));

        Test.Waiter w = new Test.Waiter();
        target.invokeAsync(req, Duration.ofSeconds(5), w);
        assertFalse(w.isDone());
        barrier.breakIt();
        w.waitDone();
        assertTrue(w.isDone());

        assertTrue(!req.isError());
        assertEquals(1, req.returnValues().size());
        assertEquals("abcdef", req.returnValues().get(0).asString());
    }

    @org.junit.Test
    public void testFilterIsInvoked() {
        Request req = new Request("concat");
        req.parameters().add(new StringValue("abc"));
        req.parameters().add(new StringValue("def"));
        assertFalse(filter.invoked);
        Test.Waiter w = new Test.Waiter();
        target.invokeAsync(req, Duration.ofSeconds(10), w);
        assertFalse(w.isDone());
        barrier.breakIt();
        w.waitDone();
        assertTrue(w.isDone());
        assertFalse(req.isError());
        assertEquals("abcdef", req.returnValues().get(0).asString());
        assertTrue(filter.invoked);
    }

}
