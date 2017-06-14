// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.FeedParams;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.core.api.FeedClientImpl;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * Tests for the API, using dryrun option to mock gateway.
 * @author dybis
 */
public class FeedClientTest {

    private final static String DOCID = "doc_id";

    SessionParams sessionParams = new SessionParams.Builder()
            .addCluster(new Cluster.Builder()
                    .addEndpoint(Endpoint.create("hostname"))
                    .build())
            .setConnectionParams(new ConnectionParams.Builder()
                    .setDryRun(true)
                    .build())
            .build();
    final AtomicInteger resultsReceived = new AtomicInteger(0);
    FeedClient.ResultCallback resultCallback = (docId, documentResult) -> {
        assert(documentResult.isSuccess());
        assertThat(docId, is(DOCID));
        resultsReceived.incrementAndGet();
    };

    FeedClient feedClient = new FeedClientImpl(sessionParams, resultCallback, SessionFactory.createTimeoutExecutor());

    @Test
    public void testStreamAndClose() throws Exception {
        feedClient.stream(DOCID, "blob");
        feedClient.close();
        assertThat(resultsReceived.get(), is(1));
    }

    @Test
    public void testGetStatsAsJson() throws Exception {
        feedClient.stream(DOCID, "blob");
        while (resultsReceived.get() == 0) {Thread.sleep(3); }
        String stats = feedClient.getStatsAsJson();
        assertTrue(stats.contains("\"dryRun\":true"));
        feedClient.close();
    }

    @Test
    public void testFeedJson() throws Exception {
        InputStream stream = new ByteArrayInputStream((String.format("[{\"remove\": \"%s\"}]", DOCID)
                .getBytes(StandardCharsets.UTF_8)));
        AtomicInteger docCounter = new AtomicInteger(0);
        FeedClient.feedJson(stream, feedClient, docCounter);
        assertThat(docCounter.get(), is(1));
        feedClient.close();
        assertThat(resultsReceived.get(), is(1));
    }

    @Test
    public void testFeedXml() throws Exception {
        InputStream stream = new ByteArrayInputStream((String.format(
                "<document documenttype=\"music\" documentid=\"%s\">\n</document>\n", DOCID)
                .getBytes(StandardCharsets.UTF_8)));
        AtomicInteger docCounter = new AtomicInteger(0);
        FeedClient.feedXml(stream, feedClient, docCounter);
        assertThat(docCounter.get(), is(1));
        feedClient.close();
        assertThat(resultsReceived.get(), is(1));
    }
}
