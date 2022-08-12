// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import com.yahoo.security.tls.ConnectionAuthContext;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

import static com.yahoo.jrt.CryptoUtils.createTestTlsContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class EchoTest {

    TransportMetrics metrics;
    TransportMetrics.Snapshot startSnapshot;
    Supervisor server;
    Acceptor   acceptor;
    Supervisor client;
    Target     target;
    Values     refValues;
    ConnectionAuthContext connAuthCtx;

    private interface MetricsAssertions {
        void assertMetrics(TransportMetrics.Snapshot snapshot) throws AssertionError;
    }

    private interface ConnectionAuthContextAssertion {
        void assertConnectionAuthContext(ConnectionAuthContext authContext) throws AssertionError;
    }

    @Parameter(value = 0) public CryptoEngine crypto;
    @Parameter(value = 1) public MetricsAssertions metricsAssertions;
    @Parameter(value = 2) public ConnectionAuthContextAssertion connAuthCtxAssertion;


    @Parameters(name = "{0}") public static Object[] engines() {
        return new Object[][] {
                {
                        new NullCryptoEngine(),
                        (MetricsAssertions) metrics -> {
                            assertEquals(1, metrics.serverUnencryptedConnectionsEstablished());
                            assertEquals(1, metrics.clientUnencryptedConnectionsEstablished());
                        },
                        null},
                {
                        new XorCryptoEngine(),
                        null,
                        null},
                {
                        new TlsCryptoEngine(createTestTlsContext()),
                        (MetricsAssertions) metrics -> {
                            assertEquals(1, metrics.serverTlsConnectionsEstablished());
                            assertEquals(1, metrics.clientTlsConnectionsEstablished());
                        },
                        (ConnectionAuthContextAssertion) context -> {
                            List<X509Certificate> chain = context.peerCertificateChain();
                            assertEquals(1, chain.size());
                            assertEquals(CryptoUtils.certificate, chain.get(0));
                        }},
                {
                        new MaybeTlsCryptoEngine(new TlsCryptoEngine(createTestTlsContext()), false),
                        (MetricsAssertions) metrics -> {
                            assertEquals(1, metrics.serverUnencryptedConnectionsEstablished());
                            assertEquals(1, metrics.clientUnencryptedConnectionsEstablished());
                        },
                        null},
                {
                        new MaybeTlsCryptoEngine(new TlsCryptoEngine(createTestTlsContext()), true),
                        (MetricsAssertions) metrics -> {
                             assertEquals(1, metrics.serverTlsConnectionsEstablished());
                             assertEquals(1, metrics.clientTlsConnectionsEstablished());
                        },
                        (ConnectionAuthContextAssertion) context -> {
                            List<X509Certificate> chain = context.peerCertificateChain();
                            assertEquals(1, chain.size());
                            assertEquals(CryptoUtils.certificate, chain.get(0));
                        }}};
    }

    @Before
    public void setUp() throws ListenFailedException {
        metrics =  TransportMetrics.getInstance();
        startSnapshot = metrics.snapshot();
        server   = new Supervisor(new Transport("server", crypto, 1));
        client   = new Supervisor(new Transport("client", crypto, 1));
        acceptor = server.listen(new Spec(0));
        target   = client.connect(new Spec("localhost", acceptor.port()));
        server.addMethod(new Method("echo", "*", "*", this::rpc_echo));
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

    private void rpc_echo(Request req) {
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
        connAuthCtx = req.target().connectionAuthContext();
    }

    @org.junit.Test
    public void testEcho() {
        Request req = new Request("echo");
        Values p = req.parameters();
        for (int i = 0; i < refValues.size(); i++) {
            p.add(refValues.get(i));
        }
        target.invokeSync(req, Duration.ofSeconds(60));
        assertTrue(req.checkReturnTypes("bBhHiIlLfFdDxXsS"));
        assertTrue(Test.equals(req.returnValues(), req.parameters()));
        assertTrue(Test.equals(req.returnValues(), refValues));
        assertTrue(Test.equals(req.parameters(), refValues));
        if (metricsAssertions != null) {
            metricsAssertions.assertMetrics(metrics.snapshot().changesSince(startSnapshot));
        }
        if (connAuthCtxAssertion != null) {
            assertNotNull(connAuthCtx);
            connAuthCtxAssertion.assertConnectionAuthContext(connAuthCtx);
        }
    }
}
