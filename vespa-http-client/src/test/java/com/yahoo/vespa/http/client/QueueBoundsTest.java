// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.FeedParams;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.handlers.V3MockParsingRequestHandler;
import org.junit.Test;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.yahoo.vespa.http.client.TestUtils.writeDocument;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * Only runs on screwdriver to save time!
 *
 * @author Einar M R Rosenvinge
 */
@SuppressWarnings("deprecation")
public class QueueBoundsTest extends TestOnCiBuildingSystemOnly {

    public static final List<TestDocument> documents;

    static {
        List<TestDocument> docs = new ArrayList<>();
        docs.add(new TestDocument("id:music:music::http://music.yahoo.com/bobdylan/BestOf",
                              ("<document documenttype=\"music\" documentid=\"id:music:music::http://music.yahoo.com/bobdylan/BestOf\">\n" +
                               "  <title>Best of Bob Dylan</title>\n" +
                               "</document>\n").getBytes(StandardCharsets.UTF_8)));
        docs.add(new TestDocument("id:music:music::http://music.yahoo.com/oleivars/BestOf",
                              ("<document documenttype=\"music\" documentid=\"id:music:music::http://music.yahoo.com/oleivars/BestOf\">\n" +
                               "  <title>Best of Ole Ivars</title>\n" +
                               "</document>\n").getBytes(StandardCharsets.UTF_8)));
        docs.add(new TestDocument("id:music:music::http://music.yahoo.com/bjarnefritjofs/BestOf",
                              ("<document documenttype=\"music\" documentid=\"id:music:music::http://music.yahoo.com/bjarnefritjofs/BestOf\">\n" +
                               "  <title>Best of Bjarne Fritjofs</title>\n" +
                               "</document>\n").getBytes(StandardCharsets.UTF_8)));
        docs.add(new TestDocument("id:music:music::http://music.yahoo.com/larryingvars/BestOf",
                              ("<document documenttype=\"music\" documentid=\"id:music:music::http://music.yahoo.com/larryingvars/BestOf\">\n" +
                               "  <title>Best of Larry Ingvars</title>\n" +
                               "</document>\n").getBytes(StandardCharsets.UTF_8)));
        documents = Collections.unmodifiableList(docs);
    }

    @Test
    public void requireThatFullInputQueueBlocksAndUnblocks() throws Exception {
        V3MockParsingRequestHandler mockXmlParsingRequestHandler = new V3MockParsingRequestHandler();
        mockXmlParsingRequestHandler.setScenario(V3MockParsingRequestHandler.Scenario.DONT_ACCEPT_VERSION);
        try (Server server = new Server(mockXmlParsingRequestHandler, 0);
             Session session =
                     new com.yahoo.vespa.http.client.core.api.SessionImpl(
                             new SessionParams.Builder()
                                     .addCluster(new Cluster.Builder()
                                                         .addEndpoint(Endpoint.create("localhost", server.getPort(), false))
                                                         .build())
                                     .setFeedParams(new FeedParams.Builder()
                                                            .setMaxChunkSizeBytes(1)
                                                            .setMaxInFlightRequests(1)
                                                            .setLocalQueueTimeOut(40000)
                                                            .build())
                                     .setConnectionParams(new ConnectionParams.Builder()
                                                                  .setNumPersistentConnectionsPerEndpoint(1)
                                                                  .build())
                                     .setClientQueueSize(2)
                                     .build(),
                             SessionFactory.createTimeoutExecutor())) {
            FeederThread feeder = new FeederThread(session);
            try {
                feeder.start();
                assertFeedNotBlocking(feeder, 0);
                assertThat(session.results().size(), is(0));
                assertFeedNotBlocking(feeder, 1);
                assertThat(session.results().size(), is(0));
                CountDownLatch lastPostFeed = assertFeedBlocking(feeder, 2);
                assertThat(session.results().size(), is(0));

                mockXmlParsingRequestHandler.setScenario(V3MockParsingRequestHandler.Scenario.ALL_OK);
                lastPostFeed.await(60, TimeUnit.SECONDS);
                assertThat(lastPostFeed.getCount(), equalTo(0L));
                assertResultQueueSize(session, 3, 60, TimeUnit.SECONDS);
            } finally {
                feeder.stop();
            }
        }
    }

    @Test
    public void requireThatFullIntermediateQueueBlocksAndUnblocks() throws Exception {
        V3MockParsingRequestHandler slowHandler =
                new V3MockParsingRequestHandler("B", HttpServletResponse.SC_OK,
                                                   V3MockParsingRequestHandler.Scenario.DELAYED_RESPONSE);

        try (Server serverA = new Server(new V3MockParsingRequestHandler("A"), 0);
             Server serverB = new Server(slowHandler, 0);
             com.yahoo.vespa.http.client.core.api.SessionImpl session = new com.yahoo.vespa.http.client.core.api.SessionImpl(
                     new SessionParams.Builder()
                             .addCluster(new Cluster.Builder()
                                                 .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                                 .build())
                             .addCluster(new Cluster.Builder()
                                                 .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                                 .build())
                             .setFeedParams(new FeedParams.Builder()
                                                    .setMaxChunkSizeBytes(1)
                                                    .build())
                             .setConnectionParams(new ConnectionParams.Builder()
                                                          .setNumPersistentConnectionsPerEndpoint(1)
                                                          .build())
                             .setClientQueueSize(6)  //3  per cluster
                             .build(), SessionFactory.createTimeoutExecutor())) {

            FeederThread feeder = new FeederThread(session);
            try {
                feeder.start();
                assertFeedNotBlocking(feeder, 0);
                assertFeedNotBlocking(feeder, 1);
                assertFeedNotBlocking(feeder, 2);

                //A input queue size now: 3
                //B input queue size now: 3
                //feeder thread not blocked
                //intermediate result queue size now: 3
                //result queue size now: 0
                assertResultQueueSize(session, 0, 60, TimeUnit.SECONDS);
                assertIncompleteResultQueueSize(session, 3, 60, TimeUnit.SECONDS);

                assertResultQueueSize(session, 0, 60, TimeUnit.SECONDS);
                assertIncompleteResultQueueSize(session, 3, 60, TimeUnit.SECONDS);

                //server B is slow, server A is fast


                //A input queue size now: 0
                //B input queue size now: 2, IOThread writing 1 and blocked because of no response yet
                //feeder thread still not blocked
                //intermediate result queue size now: 3
                //result queue size now: 0
                assertResultQueueSize(session, 0, 60, TimeUnit.SECONDS);
                assertIncompleteResultQueueSize(session, 3, 60, TimeUnit.SECONDS);

                CountDownLatch lastPostFeed = assertFeedBlocking(feeder, 3);

                //A input queue size now: 0
                //B input queue size now: 2, IOThread writing 1 and blocked because of no response yet
                //feeder thread blocking with 1 op
                //intermediate result queue size now: 3
                //result queue size now: 0

                slowHandler.poke();

                //A input queue size now: 0
                //B input queue size now: 2, IOThread writing 1 and blocked because of no response again
                //feeder thread unblocked
                //intermediate result queue size now: 3
                //result queue size now: 1

                lastPostFeed.await(60, TimeUnit.SECONDS);
                assertThat(lastPostFeed.getCount(), equalTo(0L));

                slowHandler.pokeAllAndUnblockFromNowOn();

                //A input queue size now: 0
                //B input queue size now: 0, IOThread not blocked
                //feeder thread unblocked
                //intermediate result queue size now: 0
                //result queue size now: 4

                assertResultQueueSize(session, 4, 60, TimeUnit.SECONDS);
            } finally {
                slowHandler.pokeAllAndUnblockFromNowOn();
                feeder.stop();
            }
        }
    }

    @Test
    public void testErrorRecovery() throws Exception {
        V3MockParsingRequestHandler mockXmlParsingRequestHandler = new V3MockParsingRequestHandler();
        mockXmlParsingRequestHandler.setScenario(V3MockParsingRequestHandler.Scenario.DONT_ACCEPT_VERSION);
        try (Server server = new Server(mockXmlParsingRequestHandler, 0);
             Session session =
                     new com.yahoo.vespa.http.client.core.api.SessionImpl(
                             new SessionParams.Builder()
                                     .addCluster(new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", server.getPort(), false))
                                             .build())
                                     .setFeedParams(new FeedParams.Builder()
                                             .setMaxChunkSizeBytes(1)
                                             .setMaxInFlightRequests(1)
                                             .setLocalQueueTimeOut(3000)
                                             .build())
                                     .setConnectionParams(new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(1)
                                             .build())
                                     .setClientQueueSize(1)
                                     .build(),
                             SessionFactory.createTimeoutExecutor())) {
            FeederThread feeder = new FeederThread(session);
            feeder.start();
            try {
                {
                    System.out.println("We start with failed connection, post a document.");
                    assertFeedNotBlocking(feeder, 0);
                    assertThat(session.results().size(), is(0));

                    CountDownLatch lastPostFeed = assertFeedBlocking(feeder, 1);
                    System.out.println("No result so far.");
                    assertThat(session.results().size(), is(0));
                    System.out.println("Make connection ok.");
                    mockXmlParsingRequestHandler.setScenario(V3MockParsingRequestHandler.Scenario.ALL_OK);
                    assert(lastPostFeed.await(120, TimeUnit.SECONDS));
                    assertThat(lastPostFeed.getCount(), equalTo(0L));
                    assertResultQueueSize(session, 2, 120, TimeUnit.SECONDS);
                }

                System.out.println("Take down connection");

                mockXmlParsingRequestHandler.setScenario(V3MockParsingRequestHandler.Scenario.DONT_ACCEPT_VERSION);
                {
                    assertFeedNotBlocking(feeder, 2);
                    System.out.println("Fed one document, fit in queue.");
                    assertThat(session.results().size(), is(2));
                    System.out.println("Fed one document more, wait for failure.");

                    assertFeedNotBlocking(feeder, 3);
                    System.out.println("Wait for results for all three documents.");
                    while (session.results().size() != 3) {
                        Thread.sleep(1);
                    }
                    System.out.println("Back to ok, test feeding again.");
                    mockXmlParsingRequestHandler.setScenario(V3MockParsingRequestHandler.Scenario.ALL_OK);
                    assertResultQueueSize(session, 4, 120, TimeUnit.SECONDS);
                }
                int errors = 0;
                for (Result result : session.results()) {
                    assertThat(result.getDetails().size(), is(1));
                    if (! result.isSuccess()) {
                        errors++;
                    }
                }
                assertThat(errors, is(1));
            } finally {
                feeder.stop();
            }
        }
    }

    private void assertResultQueueSize(Session session, int size, long timeout, TimeUnit timeUnit) throws InterruptedException {
        long timeoutMs = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
        long waitTimeMs = 0;
        while (true) {
            if (session.results().size() == size) {
                break;
            }
            Thread.sleep(100);
            waitTimeMs += 100;
            if (waitTimeMs > timeoutMs) {
                fail("Queue never reached size " + size + " before timeout of " + timeout + " " + timeUnit + ". Size now: " + session.results().size());
            }
        }
    }

    private void assertIncompleteResultQueueSize(com.yahoo.vespa.http.client.core.api.SessionImpl session, int size, long timeout, TimeUnit timeUnit) throws InterruptedException {
        long timeoutMs = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
        long waitTimeMs = 0;
        while (true) {
            if (session.getIncompleteResultQueueSize() == size) {
                break;
            }
            Thread.sleep(100);
            waitTimeMs += 100;
            if (waitTimeMs > timeoutMs) {
                fail("Queue of incomplete results never reached size " + size + " before timeout of " + timeout + " " + timeUnit + ". Size now: " + session.getIncompleteResultQueueSize());
            }
        }
    }

    private CountDownLatch assertFeedBlocking(FeederThread feeder, int idx) throws InterruptedException {
        CountDownLatch preFeed = new CountDownLatch(1);
        CountDownLatch postFeed = new CountDownLatch(1);
        feeder.documents.add(new Triplet<>(preFeed, documents.get(idx), postFeed));
        preFeed.await(60, TimeUnit.SECONDS);
        assertThat(preFeed.getCount(), equalTo(0L));
        postFeed.await(2, TimeUnit.SECONDS);
        assertThat(feeder.getState(), not(equalTo(Thread.State.RUNNABLE)));
        assertThat(postFeed.getCount(), equalTo(1L));
        return postFeed;
    }

    private void assertFeedNotBlocking(FeederThread feeder, int idx) throws InterruptedException {
        CountDownLatch preFeed = new CountDownLatch(1);
        CountDownLatch postFeed = new CountDownLatch(1);
        feeder.documents.add(new Triplet<>(preFeed, documents.get(idx), postFeed));
        preFeed.await(60, TimeUnit.SECONDS);
        assertThat(preFeed.getCount(), equalTo(0L));
        postFeed.await(60, TimeUnit.SECONDS);
        assertThat(postFeed.getCount(), equalTo(0L));
    }

    public static class Triplet<F, S, T> {
        public final F first;
        public final S second;
        public final T third;

        public Triplet(final F first, final S second, final T third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }
    }

    private class FeederThread implements Runnable {
        private final Session session;
        private final BlockingQueue<Triplet<CountDownLatch, TestDocument, CountDownLatch>> documents = new LinkedBlockingQueue<>();
        private final Thread thread;
        private volatile boolean shouldRun = true;

        FeederThread(Session session) {
            this.session = session;
            this.thread = new Thread(this);
        }

        public void start() {
            this.thread.start();
        }

        public void stop() {
            shouldRun = false;
            thread.interrupt();
        }

        public Thread.State getState() {
            return thread.getState();
        }

        @Override
        public void run() {
            try {
                while (shouldRun) {
                    Triplet<CountDownLatch, TestDocument, CountDownLatch> triplet = documents.poll(200, TimeUnit.MILLISECONDS);
                    if (triplet == null) {
                        continue;
                    }
                    triplet.first.countDown();
                    writeDocument(session, triplet.second);
                    triplet.third.countDown();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            } catch (InterruptedException e) {
                //ignore
            } catch (RuntimeException re) {
                if (re.getCause() == null || (!(re.getCause() instanceof InterruptedException))) {
                    re.printStackTrace();
                }
            }

        }

    }
}
