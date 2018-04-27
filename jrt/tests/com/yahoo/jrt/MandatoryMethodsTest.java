// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import org.junit.After;
import org.junit.Before;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class MandatoryMethodsTest {

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
    public void testPing() {
        Request req = new Request("frt.rpc.ping");
        target.invokeSync(req, 5.0);

        assertFalse(req.isError());
        assertEquals(0, req.returnValues().size());
    }

    @org.junit.Test
    public void testGetMethodList() {
        Request req = new Request("frt.rpc.getMethodList");
        target.invokeSync(req, 5.0);

        assertFalse(req.isError());
        assertTrue(req.checkReturnTypes("SSS"));
        String[] names = req.returnValues().get(0).asStringArray();
        String[] param = req.returnValues().get(1).asStringArray();
        String[] ret   = req.returnValues().get(2).asStringArray();
        assertEquals(3, names.length);
        assertTrue(names.length == param.length);
        assertTrue(names.length == ret.length);
        HashSet<String> foundSet = new HashSet<String>();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals("frt.rpc.ping")) {
                assertEquals("", param[i]);
                assertEquals("", ret[i]);
            } else if (names[i].equals("frt.rpc.getMethodList")) {
                assertEquals("", param[i]);
                assertEquals("SSS", ret[i]);
            } else if (names[i].equals("frt.rpc.getMethodInfo")) {
                assertEquals("s", param[i]);
                assertEquals("sssSSSS", ret[i]);
            }
            foundSet.add(names[i]);
        }
        assertEquals(3, foundSet.size());
        assertTrue(foundSet.contains("frt.rpc.ping"));
        assertTrue(foundSet.contains("frt.rpc.getMethodList"));
        assertTrue(foundSet.contains("frt.rpc.getMethodInfo"));
    }

    @org.junit.Test
    public void testGetMethodInfo() {
        Request req = new Request("frt.rpc.getMethodInfo");
        req.parameters().add(new StringValue("frt.rpc.getMethodInfo"));
        target.invokeSync(req, 5.0);

        assertFalse(req.isError());
        assertTrue(req.checkReturnTypes("sssSSSS"));

        String desc  = req.returnValues().get(0).asString();
        String param = req.returnValues().get(1).asString();
        String ret   = req.returnValues().get(2).asString();
        String[] paramName = req.returnValues().get(3).asStringArray();
        String[] paramDesc = req.returnValues().get(4).asStringArray();
        String[] retName   = req.returnValues().get(5).asStringArray();
        String[] retDesc   = req.returnValues().get(6).asStringArray();
        assertEquals("s", param);
        assertEquals("sssSSSS", ret);
        assertEquals(1, paramName.length);
        assertTrue(paramName.length == paramDesc.length);
        assertEquals(7, retName.length);
        assertTrue(retName.length == retDesc.length);
    }

}
