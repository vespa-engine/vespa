// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BackTargetTest {

    Supervisor server;
    Acceptor   acceptor;
    Supervisor client;
    Target     target;
    int        serverValue;
    int        clientValue;
    Target     serverBackTarget;
    Target     clientBackTarget;

    @Before
    public void setUp() throws ListenFailedException {
        server   = new Supervisor(new Transport());
        client   = new Supervisor(new Transport());
        acceptor = server.listen(new Spec(Test.PORT));
        target   = client.connect(new Spec("localhost", Test.PORT));

        server.addMethod(new Method("inc", "", "", this, "server_inc"));
        server.addMethod(new Method("sample_target", "", "", this,
                                    "server_sample_target"));
        server.addMethod(new Method("back_inc", "", "", this, "back_inc"));

        client.addMethod(new Method("inc", "", "", this, "client_inc"));
        client.addMethod(new Method("sample_target", "", "", this,
                                    "client_sample_target"));
        client.addMethod(new Method("back_inc", "", "", this, "back_inc"));

        serverValue = 0;
        clientValue = 0;
        serverBackTarget = null;
        clientBackTarget = null;
    }

    @After
    public void tearDown() {
        target.close();
        acceptor.shutdown().join();
        client.transport().shutdown().join();
        server.transport().shutdown().join();
    }

    public void server_inc(Request req) {
        serverValue++;
    }

    public void server_sample_target(Request req) {
        serverBackTarget = req.target();
    }

    public void client_inc(Request req) {
        clientValue++;
    }

    public void client_sample_target(Request req) {
        clientBackTarget = req.target();
    }

    public void back_inc(Request req) {
        Target t = req.target();
        t.invokeVoid(new Request("inc"));
    }

    private void checkValues(int server, int client) {
        assertEquals(server, serverValue);
        assertEquals(client, clientValue);
    }

    private void checkTargets(boolean server, boolean client) {
        assertTrue(server == (serverBackTarget != null));
        assertTrue(client == (clientBackTarget != null));
    }

    @org.junit.Test
    public void testBackTarget() {
        checkTargets(false, false);
        target.invokeSync(new Request("sample_target"), 5.0);
        checkTargets(true, false);
        serverBackTarget.invokeSync(new Request("sample_target"), 5.0);
        checkTargets(true, true);

        checkValues(0, 0);
        target.invokeSync(new Request("inc"), 5.0);
        checkValues(1, 0);
        serverBackTarget.invokeSync(new Request("inc"), 5.0);
        checkValues(1, 1);
        clientBackTarget.invokeSync(new Request("inc"), 5.0);
        checkValues(2, 1);

        target.invokeSync(new Request("back_inc"), 5.0);
        checkValues(2, 2);
        serverBackTarget.invokeSync(new Request("back_inc"), 5.0);
        checkValues(3, 2);
        clientBackTarget.invokeSync(new Request("back_inc"), 5.0);
        checkValues(3, 3);
    }

    @org.junit.Test
    public void testBogusBackTarget() {
        Request req = new Request("inc");
        try {
            req.target();
            assertTrue(false);
        } catch (IllegalStateException e) {}
    }

}
