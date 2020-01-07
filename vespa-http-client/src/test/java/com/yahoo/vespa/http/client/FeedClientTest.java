// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.core.api.FeedClientImpl;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the API, using dryrun option to mock gateway.
 *
 * @author dybis
 */
public class FeedClientTest {

    private final static String DOCID = "doc_id";

    SessionParams sessionParams = new SessionParams.Builder()
            .addCluster(new Cluster.Builder()
                    .addEndpoint(Endpoint.create("localhost"))
                    .build())
            .setConnectionParams(new ConnectionParams.Builder()
                    .setDryRun(true)
                    .build())
            .build();
    final AtomicInteger resultsReceived = new AtomicInteger(0);

    FeedClient.ResultCallback resultCallback = (docId, documentResult) -> {
        assertTrue(documentResult.isSuccess());
        assertEquals(DOCID, docId);
        resultsReceived.incrementAndGet();
    };

    FeedClient feedClient = new FeedClientImpl(sessionParams, resultCallback, FeedClientFactory.createTimeoutExecutor());

    @Test
    public void testStreamAndClose() {
        feedClient.stream(DOCID, "blob");
        feedClient.close();
        assertEquals(1, resultsReceived.get());
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
    public void testFeedJson() {
        InputStream stream = new ByteArrayInputStream((String.format("[{\"remove\": \"%s\"}]", DOCID)
                .getBytes(StandardCharsets.UTF_8)));
        AtomicInteger docCounter = new AtomicInteger(0);
        FeedClient.feedJson(stream, feedClient, docCounter);
        assertEquals(1, docCounter.get());
        feedClient.close();
        assertEquals(1, resultsReceived.get());
    }

    @Test
    public void testFeedXml() {
        InputStream stream = new ByteArrayInputStream((String.format(
                "<document documenttype=\"music\" documentid=\"%s\">\n</document>\n", DOCID)
                .getBytes(StandardCharsets.UTF_8)));
        AtomicInteger docCounter = new AtomicInteger(0);
        FeedClient.feedXml(stream, feedClient, docCounter);
        assertEquals(1, docCounter.get());
        feedClient.close();
        assertEquals(1, resultsReceived.get());
    }

}

