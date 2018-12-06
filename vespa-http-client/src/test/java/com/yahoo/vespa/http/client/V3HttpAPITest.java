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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.yahoo.vespa.http.client.TestUtils.getResults;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * Only runs on screwdriver to save time!
 *
 * @author Einar M R Rosenvinge
 */
public class V3HttpAPITest extends TestOnCiBuildingSystemOnly {

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
        documents = Collections.unmodifiableList(docs);
    }

    private void writeDocuments(Session session) throws IOException {
        TestUtils.writeDocuments(session, documents);
    }

    private void writeDocument(Session session) throws IOException {
        TestUtils.writeDocuments(session, Collections.<TestDocument>singletonList(documents.get(0)));
    }

    private void testServerWithMock(V3MockParsingRequestHandler serverMock, boolean failFast, boolean conditionNotMet) throws Exception {
        try (Server server = new Server(serverMock, 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(0)
                                             .build())
                             .setFeedParams(new FeedParams.Builder()
                                     .setLocalQueueTimeOut(failFast ? 0 : 120000)
                                     .setServerTimeout(120, TimeUnit.SECONDS)
                                     .setClientTimeout(120, TimeUnit.SECONDS)
                                     .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", server.getPort(), false))
                                             .build())
                             .build())) {

            writeDocument(session);
            Map<String, Result> results = getResults(session, 1);
            assertThat(results.size(), is(1));

            TestDocument document = documents.get(0);
            Result r = results.remove(document.getDocumentId());
            assertThat(r, not(nullValue()));
            if (conditionNotMet) {
                assertThat(r.getDetails().iterator().next().getResultType(), is(Result.ResultType.CONDITION_NOT_MET));
            }
            assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
            assertThat(results.isEmpty(), is(true));
        }
    }

    @Test
    public void requireThatSingleDestinationWorks() throws Exception {
        try (Server server = new Server(new V3MockParsingRequestHandler(), 0);
             Session session = SessionFactory.create(Endpoint.create("localhost", server.getPort(), false))) {

            writeDocuments(session);
            Map<String, Result> results = getResults(session, documents.size());
            assertThat(results.size(), is(documents.size()));

            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r, not(nullValue()));
                assertThat(r.getDetails().toString(), r.isSuccess(), is(true));
            }
            assertThat(results.isEmpty(), is(true));
        }
    }

    @Test
    public void requireThatBadResponseCodeFails() throws Exception {
        testServerWithMock(new V3MockParsingRequestHandler(401/*Unauthorized*/), true, false);
        testServerWithMock(new V3MockParsingRequestHandler(403/*Forbidden*/), true, false);
        testServerWithMock(new V3MockParsingRequestHandler(407/*Proxy Authentication Required*/), true, false);
    }

    @Test
    public void requireThatUnexpectedVersionIsHandledProperly() throws Exception {
        testServerWithMock(new V3MockParsingRequestHandler(
                200, V3MockParsingRequestHandler.Scenario.RETURN_UNEXPECTED_VERSION), true, false);
    }

    @Test
    public void requireThatNonAcceptedVersionIsHandledProperly() throws Exception {
        testServerWithMock(new V3MockParsingRequestHandler(
                200, V3MockParsingRequestHandler.Scenario.DONT_ACCEPT_VERSION), true, false);
    }

    @Test
    public void requireThatNon200OkIsHandledProperly() throws Exception {
        testServerWithMock(new V3MockParsingRequestHandler(
                200, V3MockParsingRequestHandler.Scenario.INTERNAL_SERVER_ERROR), true, false);
    }

    @Test
    public void requireThatMbusErrorIsHandledProperly() throws Exception {
        testServerWithMock(new V3MockParsingRequestHandler(
                200, V3MockParsingRequestHandler.Scenario.MBUS_RETURNED_ERROR), false, false);
    }

    @Test
    public void requireThatTimeoutWorks() throws Exception {
        try (Server server = new Server(new V3MockParsingRequestHandler(
                200, V3MockParsingRequestHandler.Scenario.NEVER_RETURN_ANY_RESULTS), 0);
             Session session = SessionFactory.create(
                     new SessionParams.Builder()
                             .setFeedParams(new FeedParams.Builder()
                                     .setLocalQueueTimeOut(0)
                                     .build())
                             .setConnectionParams(
                                     new ConnectionParams.Builder()
                                             .setNumPersistentConnectionsPerEndpoint(1)
                                             .setMaxRetries(2)
                                             .build())
                             .setFeedParams(
                                     new FeedParams.Builder()
                                             .setServerTimeout(500, TimeUnit.MILLISECONDS)
                                             .setClientTimeout(500, TimeUnit.MILLISECONDS)
                                             .build())
                             .addCluster(
                                     new Cluster.Builder()
                                             .addEndpoint(Endpoint.create("localhost", server.getPort(), false))
                                             .build())
                             .build())) {

            writeDocuments(session);

            Map<String, Result> results = getResults(session, documents.size());
            assertThat(results.size(), is(documents.size()));

            for (TestDocument document : documents) {
                Result r = results.remove(document.getDocumentId());
                assertThat(r, not(nullValue()));
                assertThat(r.getDetails().toString(), r.isSuccess(), is(false));
                assertThat(r.getDetails().iterator().next().getResultType(), is(Result.ResultType.TRANSITIVE_ERROR));
            }
            assertThat(results.isEmpty(), is(true));
        }
    }

    @Test
    public void requireThatCouldNotFeedErrorIsHandledProperly() throws Exception {
        testServerWithMock(new V3MockParsingRequestHandler(
                200, V3MockParsingRequestHandler.Scenario.COULD_NOT_FEED), false, false);
    }

    @Test
    public void requireThatImmediateDisconnectIsHandledProperly() throws Exception {
        testServerWithMock(new V3MockParsingRequestHandler(
                200, V3MockParsingRequestHandler.Scenario.DISCONNECT_IMMEDIATELY), true, false);
    }
    @Test
    public void testConditionNotMet() throws Exception {
        testServerWithMock(new V3MockParsingRequestHandler(
                200, V3MockParsingRequestHandler.Scenario.CONDITON_NOT_MET), false, true);
    }
}
