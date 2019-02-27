// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jrt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static com.yahoo.jrt.CryptoUtils.createTestTlsContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LatencyTest {
    private static final Logger log = Logger.getLogger(LatencyTest.class.getName());

    private static class Server implements AutoCloseable {
        private Supervisor orb;
        private Acceptor acceptor;
        public Server(CryptoEngine crypto) throws ListenFailedException {
            orb = new Supervisor(new Transport(crypto));
            acceptor = orb.listen(new Spec(0));
            orb.addMethod(new Method("inc", "i", "i", this, "rpc_inc"));
        }
        public Target connect() {
            return orb.connect(new Spec("localhost", acceptor.port()));
        }
        public void rpc_inc(Request req) {
            req.returnValues().add(new Int32Value(req.parameters().get(0).asInt32() + 1));
        }
        public void close() {
            acceptor.shutdown().join();
            orb.transport().shutdown().join();
        }
    }

    private void measureLatency(String prefix, Server server, boolean reconnect) {
        int value = 100;
        List<Double> list = new ArrayList<>();
        Target target = server.connect();
        for (int i = 0; i < 64; ++i) {
            long before = System.nanoTime();
            if (reconnect) {
                target.close();
                target = server.connect();
            }
            Request req = new Request("inc");
            req.parameters().add(new Int32Value(value));
            target.invokeSync(req, 60.0);
            assertTrue(req.checkReturnTypes("i"));
            assertEquals(value + 1, req.returnValues().get(0).asInt32());
            value++;
            long duration = System.nanoTime() - before;
            list.add(duration / 1000000.0);
        }
        target.close();
        Collections.sort(list);
        log.info(prefix + "invocation latency: " + list.get(list.size() / 2) + " ms");
    }

    @org.junit.Test
    public void testNullCryptoLatency() throws ListenFailedException {
        try (Server server = new Server(new NullCryptoEngine())) {
            measureLatency("[null crypto, no reconnect] ", server, false);
            measureLatency("[null crypto, reconnect] ", server, true);
        }
    }

    @org.junit.Test
    public void testXorCryptoLatency() throws ListenFailedException {
        try (Server server = new Server(new XorCryptoEngine())) {
            measureLatency("[xor crypto, no reconnect] ", server, false);
            measureLatency("[xor crypto, reconnect] ", server, true);
        }
    }

    @org.junit.Test
    public void testTlsCryptoLatency() throws ListenFailedException {
        try (Server server = new Server(new TlsCryptoEngine(createTestTlsContext()))) {
            measureLatency("[tls crypto, no reconnect] ", server, false);
            measureLatency("[tls crypto, reconnect] ", server, true);
        }
    }
}
