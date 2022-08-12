// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jrt;

import java.time.Duration;
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
        public Network(CryptoEngine crypto, int threads, boolean dropEmpty) throws ListenFailedException {
            server = new Supervisor(new Transport("server", crypto, threads));
            client = new Supervisor(new Transport("client", crypto, threads));
            server.setDropEmptyBuffers(dropEmpty);
            client.setDropEmptyBuffers(dropEmpty);
            server.addMethod(new Method("inc", "i", "i", this::rpc_inc));
            acceptor = server.listen(new Spec(0));
        }
        public Network(CryptoEngine crypto, int threads) throws ListenFailedException { this(crypto, threads, false); }
        public Target connect() {
            return client.connect(new Spec("localhost", acceptor.port()));
        }
        private void rpc_inc(Request req) {
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

        private enum State { WARMUP, BENCHMARK, COOLDOWN }
        private long warmupEnd = 0;
        private long benchmarkEnd = 0;
        private long cooldownEnd = 0;

        private long addSecs(long ns, double s) {
            return ns + (long)(s * 1000_000_000);
        }

        private void setupBenchmark(double warmup, double benchmark, double cooldown) {
            final long now = System.nanoTime();
            warmupEnd = addSecs(now, warmup);
            benchmarkEnd = addSecs(warmupEnd, benchmark);
            cooldownEnd = addSecs(benchmarkEnd, cooldown);
        }

        private void run(int threadId) {
            try {
                barrier.await();
                State state = State.WARMUP;
                int value = 0;
                long t1 = 0;
                long t2 = 0;
                int s1 = 0;
                int s2 = 0;
                double minLatency = 1.0e99;
                Target target = network.connect();
                for (long t = System.nanoTime();
                     state != State.COOLDOWN || t < cooldownEnd;
                     t = System.nanoTime())
                {
                    if ((state == State.WARMUP) && (t >= warmupEnd)) {
                        t1 = t;
                        s1 = value;
                        state = State.BENCHMARK;
                    }
                    if ((state == State.BENCHMARK) && (t >= benchmarkEnd)) {
                        t2 = t;
                        s2 = value;
                        state = State.COOLDOWN;
                    }
                    if (reconnect) {
                        target.close();
                        target = network.connect();
                    }
                    Request req = new Request("inc");
                    req.parameters().add(new Int32Value(value));
                    target.invokeSync(req, Duration.ofSeconds(60));
                    long duration = System.nanoTime() - t;
                    assertTrue(req.checkReturnTypes("i"));
                    assertEquals(value + 1, req.returnValues().get(0).asInt32());
                    ++value;
                    double latency = (duration / 1000_000.0);
                    if (latency < minLatency) {
                        minLatency = latency;
                    }
                }
                target.close();
                double benchTime = (t2 - t1) / 1000_000_000.0;
                results[threadId] = new Result(minLatency, (s2 - s1) / benchTime);
            } catch (Throwable issue) {
                issues[threadId] = issue;
            } finally {
                latch.countDown();
            }
        }

        public Client(boolean reconnect, Network network, int numThreads,
                      double warmup, double benchmark, double cooldown)
        {
            this.reconnect = reconnect;
            this.network = network;
            this.barrier = new CyclicBarrier(numThreads, ()->setupBenchmark(warmup, benchmark, cooldown));
            this.latch = new CountDownLatch(numThreads);
            this.issues = new Throwable[numThreads];
            this.results = new Result[numThreads];
        }
        public Client(boolean reconnect, Network network, int numThreads) {
            this(reconnect, network, numThreads, 0.1, 0.5, 0.1);
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
    public void testTlsCryptoWithDropEmptyBuffersLatency() throws Throwable {
        try (Network network = new Network(new TlsCryptoEngine(createTestTlsContext()), 1, true)) {
            new Client(false, network, 1).measureLatency("[tls crypto, drop empty, no reconnect] ");
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
