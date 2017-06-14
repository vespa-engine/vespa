// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.FeedParams;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.handlers.V3MockParsingRequestHandler;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.yahoo.vespa.http.client.TestUtils.getResults;
import static com.yahoo.vespa.http.client.V3HttpAPITest.documents;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * Only runs on screwdriver to save time!
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.27
 */
public class V3HttpAPIMultiClusterTest extends TestOnCiBuildingSystemOnly {

    private void writeDocuments(Session session) throws IOException {
        TestUtils.writeDocuments(session, documents);
    }

    private void writeDocument(Session session) throws IOException {
        TestUtils.writeDocuments(session, Collections.<TestDocument>singletonList(documents.get(0)));
    }

    private void waitForHandshakesOk(int handshakes, Session session) throws InterruptedException {
        int waitedTimeMs = 0;
        while (session.getStatsAsJson().split("\"successfullHandshakes\":1").length != 1+ handshakes) {
            waitedTimeMs += 100;
            assert(waitedTimeMs < 300000);
            Thread.sleep(100);
        }
    }

    @Test
    public void testOpenClose() throws Exception {
        try (Server serverA = new Server(
                new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Session session = SessionFactory.create(new SessionParams.Builder()
                     .addCluster(new Cluster.Builder()
                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                             .build())
                     .build())) {
            assertThat(session, notNullValue());
        }
    }

    @Test
    public void testPriorityAndTraceFlag() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(
                200, V3MockParsingRequestHandler.Scenario.EXPECT_HIGHEST_PRIORITY_AND_TRACELEVEL_123), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(new Cluster.Builder()
                                     .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                     .build())
                             .setConnectionParams(new ConnectionParams.Builder()
                                     .setTraceLevel(123)
                                     .build())
                             .setFeedParams(new FeedParams.Builder()
                                     .setMaxSleepTimeMs(0)
                                     .setPriority("HIGHEST")
                                     .build())
                             .build())) {
            writeDocuments(session);
            Map<String, Result> results = getResults(session, documents.size());
            assertThat("Results received: " + results.values(), results.size(), is(documents.size()));
            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r.getDetails().toString(), r.isSuccess(), is(true));
            }
        }
    }

    @Test
    public void testRetries() throws Exception {
        V3MockParsingRequestHandler unstableServer =
                new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK);
        V3MockParsingRequestHandler failedServer =
                new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK);
        try (Server serverA = new Server(failedServer, 0);
             Server serverB = new Server(unstableServer, 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build()
                             )
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build()
                             )
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(2)
                                             .setMinTimeBetweenRetries(200, TimeUnit.MILLISECONDS)
                                             .build()
                             )
                             .build()
             )) {
            waitForHandshakesOk(2, session);
            // Both servers worked fine so far, handshake established. Now fail transient when trying to send
            // data on both. This should cause the OperationProcessor to retry the document. One of the server
            // will come up, but not the other.
            unstableServer.setScenario(V3MockParsingRequestHandler.Scenario.SERVER_ERROR_TWICE_THEN_OK);
            // This will cause retries, but it will not come up.
            failedServer.setScenario(V3MockParsingRequestHandler.Scenario.COULD_NOT_FEED);
            writeDocuments(session);
            Map<String, Result> results = getResults(session, documents.size());
            assertThat("Results received: " + results.values(), results.size(), is(documents.size()));

            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r, not(nullValue()));
                assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
                assertThat(r.getDetails().size(), is(2));
                assert(r.getDetails().get(0).getResultType() != r.getDetails().get(1).getResultType());
            }
            assertThat(results.isEmpty(), is(true));
        }
    }

    @Test
    public void requireThatFeedingWorks() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverC = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .build())) {

            writeDocuments(session);
            Map<String, Result> results = getResults(session, documents.size());
            assertThat("Results received: " + results.values(), results.size(), is(documents.size()));

            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r, not(nullValue()));
                assertThat(r.getDetails().toString(), r.isSuccess(), is(true));
                assertThat(r.getDetails().size(), is(3));
                assertThat(r.getDetails().get(0).getTraceMessage(), is("Trace message"));
            }
            assertThat(results.isEmpty(), is(true));
            final String stats = session.getStatsAsJson();
            assertThat(stats, containsString("maxChunkSizeBytes\":51200"));
        }
    }

    @Test
    public void requireThatOneImmediateDisconnectWorks() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverC = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.DISCONNECT_IMMEDIATELY), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(1)
                                             .setLocalQueueTimeOut(1000)
                                             .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(1)
                                             .build())
                             .build())) {

            writeDocuments(session);  //cannot fail here, they are just enqueued

            Map<String, Result> results = getResults(session, documents.size());
            assertThat(results.size(), is(documents.size()));

            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r, not(nullValue()));
                assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
                assertThat(r.getDetails().size(), is(3));

                Map<Endpoint, Result.Detail> details = new HashMap<>();
                for (Result.Detail detail : r.getDetails()) {
                    details.put(detail.getEndpoint(), detail);
                }
                assertThat(details.get(Endpoint.create("localhost", serverA.getPort(), false)).getResultType(), is(Result.ResultType.OPERATION_EXECUTED));
                assertThat(details.get(Endpoint.create("localhost", serverB.getPort(), false)).getResultType(), is(Result.ResultType.OPERATION_EXECUTED));
                assertThat(details.get(Endpoint.create("localhost", serverC.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
            }
            assertThat(results.isEmpty(), is(true));
        }
    }



    @Test
    public void requireThatAllImmediateDisconnectWorks() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.DISCONNECT_IMMEDIATELY), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.DISCONNECT_IMMEDIATELY), 0);
             Server serverC = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.DISCONNECT_IMMEDIATELY), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(1)
                                             .setLocalQueueTimeOut(1000)
                                             .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(1)
                                             .build())
                             .build())) {

            writeDocuments(session);  //cannot fail here, they are just enqueued

            Map<String, Result> results = getResults(session, documents.size());
            assertThat(results.size(), is(documents.size()));

            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r, not(nullValue()));
                assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
                assertThat(r.getDetails().size(), is(3));

                Map<Endpoint, Result.Detail> details = new HashMap<>();
                for (Result.Detail detail : r.getDetails()) {
                    details.put(detail.getEndpoint(), detail);
                }
                assertThat(details.get(Endpoint.create("localhost", serverA.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
                assertThat(details.get(Endpoint.create("localhost", serverB.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
                assertThat(details.get(Endpoint.create("localhost", serverC.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
            }
            assertThat(results.isEmpty(), is(true));
        }
    }


    @Test
    public void requireThatOneTimeoutWorks() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverC = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.NEVER_RETURN_ANY_RESULTS), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(10000)
                                             .setServerTimeout(2, TimeUnit.SECONDS)
                                             .setClientTimeout(2, TimeUnit.SECONDS)
                                             .setLocalQueueTimeOut(2000)
                                             .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(1)
                                             .build())
                             .build())) {

            waitForHandshakesOk(3, session);
            writeDocuments(session);
            Map<String, Result> results = getResults(session, documents.size());
            assertThat(results.size(), is(documents.size()));

            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r, not(nullValue()));
                //assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
                assertThat(r.getDetails().size(), is(3));

                Map<Endpoint, Result.Detail> details = new HashMap<>();
                for (Result.Detail detail : r.getDetails()) {
                    details.put(detail.getEndpoint(), detail);
                }
                assertThat(details.get(Endpoint.create("localhost", serverA.getPort(), false)).getResultType(), is(Result.ResultType.OPERATION_EXECUTED));
                assertThat(details.get(Endpoint.create("localhost", serverB.getPort(), false)).getResultType(), is(Result.ResultType.OPERATION_EXECUTED));
                assertThat(details.get(Endpoint.create("localhost", serverC.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
                assertThat(details.get(Endpoint.create("localhost", serverC.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
            }
            assertThat(results.isEmpty(), is(true));
        }
    }

    @Test
    public void requireThatOneWrongSessionIdWorks() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverC = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.RETURN_WRONG_SESSION_ID), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(1000)
                                             .setServerTimeout(2, TimeUnit.SECONDS)
                                             .setClientTimeout(2, TimeUnit.SECONDS)
                                             .setLocalQueueTimeOut(2000)
                                             .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(0)
                                             .build())
                             .build())) {

            waitForHandshakesOk(2, session);
            writeDocuments(session);

            Map<String, Result> results = getResults(session, documents.size());
            assertThat(results.size(), is(documents.size()));

            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r, not(nullValue()));
                assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
                assertThat(r.getDetails().size(), is(3));

                Map<Endpoint, Result.Detail> details = new HashMap<>();
                for (Result.Detail detail : r.getDetails()) {
                    details.put(detail.getEndpoint(), detail);
                }
                assertThat(details.get(Endpoint.create("localhost", serverA.getPort(), false)).getResultType(), is(Result.ResultType.OPERATION_EXECUTED));
                assertThat(details.get(Endpoint.create("localhost", serverB.getPort(), false)).getResultType(), is(Result.ResultType.OPERATION_EXECUTED));
                assertThat(details.get(Endpoint.create("localhost", serverC.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
            }
            assertThat(results.isEmpty(), is(true));
        }
    }

    @Test
    public void requireThatAllWrongSessionIdWorks() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.RETURN_WRONG_SESSION_ID), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.RETURN_WRONG_SESSION_ID), 0);
             Server serverC = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.RETURN_WRONG_SESSION_ID), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(1)
                                             .setServerTimeout(2, TimeUnit.SECONDS)
                                             .setClientTimeout(2, TimeUnit.SECONDS)
                                             .setLocalQueueTimeOut(2000)
                                             .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(0)
                                             .build())
                             .build())) {

            writeDocuments(session);
            Map<String, Result> results = getResults(session, documents.size());
            assertThat(results.size(), is(documents.size()));

            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r, not(nullValue()));
                //assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
                assertThat(r.getDetails().size(), is(3));

                Map<Endpoint, Result.Detail> details = new HashMap<>();
                for (Result.Detail detail : r.getDetails()) {
                    details.put(detail.getEndpoint(), detail);
                }
                assertThat(details.get(Endpoint.create("localhost", serverA.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
                assertThat(details.get(Endpoint.create("localhost", serverB.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
                assertThat(details.get(Endpoint.create("localhost", serverC.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
            }
            assertThat(results.isEmpty(), is(true));
        }
    }

    @Test
    public void requireThatOneNonAcceptedVersionWorks() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverC = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.DONT_ACCEPT_VERSION), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(1)
                                             .setLocalQueueTimeOut(2000)
                                             .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(0)
                                             .build())
                             .build())) {

            writeDocument(session);

            Map<String, Result> results = getResults(session, 1);
            assertThat(results.size(), is(1));
            Result r = results.values().iterator().next();
            assertThat(r, not(nullValue()));
            assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
            Map<Endpoint, Result.Detail> details = new HashMap<>();
            for (Result.Detail detail : r.getDetails()) {
                details.put(detail.getEndpoint(), detail);
            }
            Result.Detail failed = details.remove(Endpoint.create("localhost", serverC.getPort(), false));
            assertThat(failed.getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
            for (Result.Detail detail : details.values()) {
                assertThat(detail.getResultType(), is(Result.ResultType.OPERATION_EXECUTED));
            }
        }
    }

    @Test
    public void requireThatAllNonAcceptedVersionWorks() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.DONT_ACCEPT_VERSION), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.DONT_ACCEPT_VERSION), 0);
             Server serverC = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.DONT_ACCEPT_VERSION), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(1)
                                             .setLocalQueueTimeOut(2000)
                                             .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(1)
                                             .build())
                             .build())) {

            writeDocuments(session);  //cannot fail here, they are just enqueued

            Map<String, Result> results = getResults(session, documents.size());
            assertThat(results.size(), is(documents.size()));

            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r, not(nullValue()));
                //assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
                assertThat(r.getDetails().size(), is(3));

                Map<Endpoint, Result.Detail> details = new HashMap<>();
                for (Result.Detail detail : r.getDetails()) {
                    details.put(detail.getEndpoint(), detail);
                }
                assertThat(details.get(Endpoint.create("localhost", serverA.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
                assertThat(details.get(Endpoint.create("localhost", serverB.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
                assertThat(details.get(Endpoint.create("localhost", serverC.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
            }
            assertThat(results.isEmpty(), is(true));
        }
    }

    @Test
    public void requireThatOneUnexpectedVersionWorks() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverC = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.RETURN_UNEXPECTED_VERSION), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(1)
                                             .setLocalQueueTimeOut(2000)
                                             .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(0)
                                             .build())
                             .build())) {

            writeDocuments(session);  //cannot fail here, they are just enqueued

            Map<String, Result> results = getResults(session, documents.size());
            assertThat(results.size(), is(documents.size()));

            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r, not(nullValue()));
                assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
                assertThat(r.getDetails().size(), is(3));

                Map<Endpoint, Result.Detail> details = new HashMap<>();
                for (Result.Detail detail : r.getDetails()) {
                    details.put(detail.getEndpoint(), detail);
                }
                assertThat(details.get(Endpoint.create("localhost", serverA.getPort(), false)).getResultType(), is(Result.ResultType.OPERATION_EXECUTED));
                assertThat(details.get(Endpoint.create("localhost", serverB.getPort(), false)).getResultType(), is(Result.ResultType.OPERATION_EXECUTED));
                assertThat(details.get(Endpoint.create("localhost", serverC.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
            }
            assertThat(results.isEmpty(), is(true));
        }
    }

    @Test
    public void requireThatAllUnexpectedVersionWorks() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.RETURN_UNEXPECTED_VERSION), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.RETURN_UNEXPECTED_VERSION), 0);
             Server serverC = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.RETURN_UNEXPECTED_VERSION), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(1)
                                             .setLocalQueueTimeOut(2000)
                                             .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(0)
                                             .build())
                             .build())) {

            writeDocument(session);

            Map<String, Result> results = getResults(session, 1);
            assertThat(results.size(), is(1));
            Result r = results.values().iterator().next();
            assertThat(r, not(nullValue()));
            assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
            for (Result.Detail detail : r.getDetails()) {
                assertThat(detail.getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
            }
        }
    }

    @Test
    public void requireThatOneInternalServerErrorWorks() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverC = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.INTERNAL_SERVER_ERROR), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(1)
                                             .setLocalQueueTimeOut(2000)
                                             .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(0)
                                             .build())
                             .build())) {

            writeDocument(session);

            Map<String, Result> results = getResults(session, 1);
            assertThat(results.size(), is(1));
            Result r = results.values().iterator().next();
            assertThat(r, not(nullValue()));
            assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
            Map<Endpoint, Result.Detail> details = new HashMap<>();
            for (Result.Detail detail : r.getDetails()) {
                details.put(detail.getEndpoint(), detail);
            }
            Result.Detail failed = details.remove(Endpoint.create("localhost", serverC.getPort(), false));
            assertThat(failed.getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
            for (Result.Detail detail : details.values()) {
                assertThat(detail.getResultType(), is(Result.ResultType.OPERATION_EXECUTED));
            }
        }
    }

    @Test
    public void requireThatAllInternalServerErrorWorks() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.INTERNAL_SERVER_ERROR), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.INTERNAL_SERVER_ERROR), 0);
             Server serverC = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.INTERNAL_SERVER_ERROR), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(1)
                                             .setLocalQueueTimeOut(2000)
                                             .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(0)
                                             .build())
                             .build())) {

            writeDocument(session);

            Map<String, Result> results = getResults(session, 1);
            assertThat(results.size(), is(1));
            Result r = results.values().iterator().next();
            assertThat(r, not(nullValue()));
            assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
            for (Result.Detail detail : r.getDetails()) {
                assertThat(detail.getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
            }
        }
    }

    @Test
    public void requireThatOneCouldNotFeedErrorWorks() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverC = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.COULD_NOT_FEED), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(1)
                                             .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(1)
                                             .build())
                             .build())) {

            writeDocuments(session);
            Map<String, Result> results = getResults(session, documents.size());
            assertThat(results.size(), is(documents.size()));

            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r, not(nullValue()));
                assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
                assertThat(r.getDetails().size(), is(3));

                Map<Endpoint, Result.Detail> details = new HashMap<>();
                for (Result.Detail detail : r.getDetails()) {
                    details.put(detail.getEndpoint(), detail);
                }
                assertThat(details.get(Endpoint.create("localhost", serverA.getPort(), false)).getResultType(), is(Result.ResultType.OPERATION_EXECUTED));
                assertThat(details.get(Endpoint.create("localhost", serverB.getPort(), false)).getResultType(), is(Result.ResultType.OPERATION_EXECUTED));
                assertThat(details.get(Endpoint.create("localhost", serverC.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
            }
            assertThat(results.isEmpty(), is(true));
        }
    }

    @Test
    public void requireThatAllCouldNotFeedErrorWorks() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.COULD_NOT_FEED), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.COULD_NOT_FEED), 0);
             Server serverC = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.COULD_NOT_FEED), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(1)
                                             .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(1)
                                             .build())
                             .build())) {

            writeDocuments(session);
            Map<String, Result> results = getResults(session, documents.size());
            assertThat(results.size(), is(documents.size()));

            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r, not(nullValue()));
                assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
                assertThat(r.getDetails().size(), is(3));

                Map<Endpoint, Result.Detail> details = new HashMap<>();
                for (Result.Detail detail : r.getDetails()) {
                    details.put(detail.getEndpoint(), detail);
                }
                assertThat(details.get(Endpoint.create("localhost", serverA.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
                assertThat(details.get(Endpoint.create("localhost", serverB.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
                assertThat(details.get(Endpoint.create("localhost", serverC.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
            }
            assertThat(results.isEmpty(), is(true));
        }
    }

    @Test
    public void requireThatOneMbusErrorWorks() throws Exception {
        final V3MockParsingRequestHandler unstableServer = new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.MBUS_RETURNED_ERROR);
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
             Server serverC = new Server(unstableServer, 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(1)
                                             .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(100)
                                             .build())
                             .build())) {

            writeDocuments(session);

            // Make it fail, but it should still retry since it is a MBUS error that is transitive
            // for the client even though fatal for message bus.
            Thread.sleep(1000);
            assertThat(session.results().size(), is(0));
            unstableServer.setScenario(V3MockParsingRequestHandler.Scenario.ALL_OK);

            Map<String, Result> results = getResults(session, documents.size());
            assertThat(results.size(), is(documents.size()));

            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r, not(nullValue()));
                assertThat(r.getDetails().toString(), r.isSuccess(), is(true));
                assertThat(r.getDetails().size(), is(3));

                Map<Endpoint, Result.Detail> details = new HashMap<>();
                for (Result.Detail detail : r.getDetails()) {
                    details.put(detail.getEndpoint(), detail);
                }
                assertThat(details.get(Endpoint.create("localhost", serverA.getPort(), false)).getResultType(), is(Result.ResultType.OPERATION_EXECUTED));
                assertThat(details.get(Endpoint.create("localhost", serverB.getPort(), false)).getResultType(), is(Result.ResultType.OPERATION_EXECUTED));
                assertThat(details.get(Endpoint.create("localhost", serverC.getPort(), false)).getResultType(), is(Result.ResultType.OPERATION_EXECUTED));

            }
            assertThat(results.isEmpty(), is(true));
        }
    }

    @Test
    public void requireThatAllMbusErrorWorks() throws Exception {
        try (Server serverA = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.MBUS_RETURNED_ERROR), 0);
             Server serverB = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.MBUS_RETURNED_ERROR), 0);
             Server serverC = new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.MBUS_RETURNED_ERROR), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build()
                             )
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverC.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(1)
                                             .build()
                             )
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(0)
                                             .build()
                             )
                             .build())) {

            writeDocuments(session);  //cannot fail here, they are just enqueued

            Map<String, Result> results = getResults(session, documents.size());
            assertThat(results.size(), is(documents.size()));

            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r, not(nullValue()));
                assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
                assertThat(r.getDetails().size(), is(3));

                Map<Endpoint, Result.Detail> details = new HashMap<>();
                for (Result.Detail detail : r.getDetails()) {
                    details.put(detail.getEndpoint(), detail);
                }
                assertThat(details.get(Endpoint.create("localhost", serverA.getPort(), false)).getResultType(), is(Result.ResultType.FATAL_ERROR));
                assertThat(details.get(Endpoint.create("localhost", serverB.getPort(), false)).getResultType(), is(Result.ResultType.FATAL_ERROR));
                assertThat(details.get(Endpoint.create("localhost", serverC.getPort(), false)).getResultType(), is(Result.ResultType.FATAL_ERROR));
            }
            assertThat(results.isEmpty(), is(true));
        }
    }

    @Test
    public void requireThatBadVipBehaviorDoesNotFailBadly() throws Exception {
        V3MockParsingRequestHandler handlerA = new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.BAD_REQUEST);
        V3MockParsingRequestHandler handlerB = new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.BAD_REQUEST);

        try (Server serverA = new Server(handlerA, 0);
             Server serverB = new Server(handlerB, 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverA.getPort(), false))
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", serverB.getPort(), false))
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setMaxSleepTimeMs(0)
                                             .setMaxChunkSizeBytes(1)
                                             .setLocalQueueTimeOut(1000)
                                             .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(1)
                                             .build())
                             .build())) {

            //Set A in bad state => A returns bad request.
            handlerA.badRequestScenarioShouldReturnBadRequest.set(true);

            //write one document, should fail
            writeDocument(session);
            Map<String, Result> results = getResults(session, 1);
            assertThat(results.size(), is(1));
            Result r = results.values().iterator().next();
            assertThat(r, not(nullValue()));
            assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
            assertThat(r.getDetails().size(), is(2));
            Map<Endpoint, Result.Detail> details = new HashMap<>();
            for (Result.Detail detail : r.getDetails()) {
                details.put(detail.getEndpoint(), detail);
            }
            assertThat(details.get(Endpoint.create("localhost", serverA.getPort(), false)).getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
            assertThat(details.get(Endpoint.create("localhost", serverB.getPort(), false)).getResultType(), is(Result.ResultType.OPERATION_EXECUTED));


            //Set B in bad state => B returns bad request.
            handlerB.badRequestScenarioShouldReturnBadRequest.set(true);
        } //try to close session

    }
}
