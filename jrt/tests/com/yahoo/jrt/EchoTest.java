// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertTrue;

public class EchoTest {

    Supervisor server;
    Acceptor   acceptor;
    Supervisor client;
    Target     target;
    Values     refValues;

    @Before
    public void setUp() throws ListenFailedException {
        server   = new Supervisor(new Transport());
        client   = new Supervisor(new Transport());
        acceptor = server.listen(new Spec(Test.PORT));
        target   = client.connect(new Spec("localhost", Test.PORT));
        server.addMethod(new Method("echo", "*", "*", this, "rpc_echo"));
        refValues = new Values();
        byte[]   dataValue   = { 1, 2, 3, 4 };
        byte[]   int8Array   = { 1, 2, 3, 4 };
        short[]  int16Array  = { 2, 4, 6, 8 };
        int[]    int32Array  = { 4, 8, 12, 16 };
        long[]   int64Array  = { 8, 16, 24, 32 };
        float[]  floatArray  = { 1.5f, 2.0f, 2.5f, 3.0f };
        double[] doubleArray = { 1.25, 1.50, 1.75, 2.00 };
        byte[][] dataArray   = {{ 1, 0, 1, 0 },
                                { 0, 2, 0, 2 },
                                { 3, 0, 3, 0 },
                                { 0, 4, 0, 4 }};
        String[] stringArray = { "one", "two", "three", "four" };
        refValues.add(new Int8Value((byte)1));
        refValues.add(new Int8Array(int8Array));
        refValues.add(new Int16Value((short)2));
        refValues.add(new Int16Array(int16Array));
        refValues.add(new Int32Value(4));
        refValues.add(new Int32Array(int32Array));
        refValues.add(new Int64Value(8));
        refValues.add(new Int64Array(int64Array));
        refValues.add(new FloatValue(2.5f));
        refValues.add(new FloatArray(floatArray));
        refValues.add(new DoubleValue(3.75));
        refValues.add(new DoubleArray(doubleArray));
        refValues.add(new DataValue(dataValue));
        refValues.add(new DataArray(dataArray));
        refValues.add(new StringValue("test"));
        refValues.add(new StringArray(stringArray));
    }

    @After
    public void tearDown() {
        target.close();
        acceptor.shutdown().join();
        client.transport().shutdown().join();
        server.transport().shutdown().join();
    }

    public void rpc_echo(Request req) {
        if (!Test.equals(req.parameters(), refValues)) {
            System.err.println("Parameters does not match reference values");
            req.setError(ErrorCode.METHOD_FAILED, "parameter mismatch");
            return;
        }
        Values p = req.parameters();
        Values r = req.returnValues();
        for (int i = 0; i < p.size(); i++) {
            r.add(p.get(i));
        }
    }

    @org.junit.Test
    public void testEcho() {
        Request req = new Request("echo");
        Values p = req.parameters();
        for (int i = 0; i < refValues.size(); i++) {
            p.add(refValues.get(i));
        }
        target.invokeSync(req, 60.0);
        assertTrue(req.checkReturnTypes("bBhHiIlLfFdDxXsS"));
        assertTrue(Test.equals(req.returnValues(), req.parameters()));
        assertTrue(Test.equals(req.returnValues(), refValues));
        assertTrue(Test.equals(req.parameters(), refValues));
    }

}
