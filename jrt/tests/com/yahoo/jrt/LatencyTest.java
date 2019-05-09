// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jrt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.logging.Logger;

import static com.yahoo.jrt.CryptoUtils.createTestTlsContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LatencyTest {
    private static final Logger log = Logger.getLogger(LatencyTest.class.getName());

    private static class Network implements AutoCloseable {
        private final Supervisor server;
        private final Supervisor client;
        private final Acceptor acceptor;
        public Network(CryptoEngine crypto, int threads) throws ListenFailedException {
            server = new Supervisor(new Transport(crypto, threads));
            client = new Supervisor(new Transport(crypto, threads));
            server.addMethod(new Method("inc", "i", "i", this, "rpc_inc"));
            acceptor = server.listen(new Spec(0));
        }
        public Target connect() {
            return client.connect(new Spec("localhost", acceptor.port()));
        }
        public void rpc_inc(Request req) {
            req.returnValues().add(new Int32Value(req.parameters().get(0).asInt32() + 1));
        }
        public void close() {
            acceptor.shutdown().join();
            client.transport().shutdown().join();
            server.transport().shutdown().join();
        }
    }

    private static class Client {

        public static class Result {
            public final double latency;
            public final double throughput;

            public Result(double ms, double cnt) {
                latency = ms;
                throughput = cnt;
            }

            public Result(Result[] results) {
                double ms = 0.0;
                double cnt = 0.0;
                for (Result r: results) {
                    ms += r.latency;
                    cnt += r.throughput;
                }
                latency = (ms / results.length);
                throughput = cnt;
            }
        }

        private final boolean reconnect;
        private final Network network;
        private final CyclicBarrier barrier;
        private final CountDownLatch latch;
        private final Throwable[] issues;
        private final Result[] results;

        private void run(int threadId) {
            try {
                barrier.await();
                int value = 100;
                final int warmupCnt = 10;
                final int benchmarkCnt = 50;
                final int cooldownCnt = 10;
                final int totalReqs = (warmupCnt + benchmarkCnt + cooldownCnt);
                long t1 = 0;
                long t2 = 0;
                List<Double> list = new ArrayList<>();
                Target target = network.connect();
                for (int i = 0; i < totalReqs; ++i) {
                    long before = System.nanoTime();
                    if (i == warmupCnt) {
                        t1 = before;
                    }
                    if (i == (warmupCnt + benchmarkCnt)) {
                        t2 = before;
                    }
                    if (reconnect) {
                        target.close();
                        target = network.connect();
                    }
                    Request req = new Request("inc");
                    req.parameters().add(new Int32Value(value));
                    target.invokeSync(req, 60.0);
                    long duration = System.nanoTime() - before;
                    assertTrue(req.checkReturnTypes("i"));
                    assertEquals(value + 1, req.returnValues().get(0).asInt32());
                    value++;
                    list.add(duration / 1000000.0);
                }
                target.close();
                Collections.sort(list);
                double benchTime = (t2 - t1) / 1000000000.0;
                results[threadId] = new Result(list.get(list.size() / 2), benchmarkCnt / benchTime);
            } catch (Throwable issue) {
                issues[threadId] = issue;
            } finally {
                latch.countDown();
            }
        }

        public Client(boolean reconnect, Network network, int numThreads) {
            this.reconnect = reconnect;
            this.network = network;
            this.barrier = new CyclicBarrier(numThreads);
            this.latch = new CountDownLatch(numThreads);
            this.issues = new Throwable[numThreads];
            this.results = new Result[numThreads];
        }

        public void measureLatency(String prefix) throws Throwable {
            for (int i = 0; i < results.length; ++i) {
                final int threadId = i;
                new Thread(()->run(threadId)).start();
            }
            latch.await();
            for (Throwable issue: issues) {
                if (issue != null) {
                    throw(issue);
                }
            }
            Result result = new Result(results);
            log.info(prefix + "latency: " + result.latency + " ms, throughput: " + result.throughput + " req/s");
        }
    }

    @org.junit.Test
    public void testNullCryptoLatency() throws Throwable {
        try (Network network = new Network(new NullCryptoEngine(), 1)) {
            new Client(false, network, 1).measureLatency("[null crypto, no reconnect] ");
            new Client(true, network, 1).measureLatency("[null crypto, reconnect] ");
        }
    }

    @org.junit.Test
    public void testXorCryptoLatency() throws Throwable {
        try (Network network = new Network(new XorCryptoEngine(), 1)) {
            new Client(false, network, 1).measureLatency("[xor crypto, no reconnect] ");
            new Client(true, network, 1).measureLatency("[xor crypto, reconnect] ");
        }
    }

    @org.junit.Test
    public void testTlsCryptoLatency() throws Throwable {
        try (Network network = new Network(new TlsCryptoEngine(createTestTlsContext()), 1)) {
            new Client(false, network, 1).measureLatency("[tls crypto, no reconnect] ");
            new Client(true, network, 1).measureLatency("[tls crypto, reconnect] ");
        }
    }

    @org.junit.Test
    public void testTransportThreadScaling() throws Throwable {
        try (Network network = new Network(new NullCryptoEngine(), 1)) {
            new Client(false, network, 64).measureLatency("[64 clients, 1/1 transport] ");
        }
        try (Network network = new Network(new NullCryptoEngine(), 4)) {
            new Client(false, network, 64).measureLatency("[64 clients, 4/4 transport] ");
        }
    }
}
