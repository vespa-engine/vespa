package com.yahoo.vespa.http.client;

import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.SessionParams;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import com.yahoo.vespa.http.client.SyncFeedClient.SyncOperation;
import com.yahoo.vespa.http.client.SyncFeedClient.SyncResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

/**
 * Tests the sync wrapper to the feed client
 *
 * @author bratseth
 */
public class SyncFeedClientTest {

    @Test
    public void testFeedJson() {
        SessionParams sessionParams = new SessionParams.Builder()
                                              .addCluster(new Cluster.Builder()
                                                                  .addEndpoint(Endpoint.create("hostname"))
                                                                  .build())
                                              .setConnectionParams(new ConnectionParams.Builder()
                                                                           .setDryRun(true)
                                                                           .build())
                                              .build();
        SyncFeedClient feedClient = new SyncFeedClient(sessionParams);


        assertFeedSuccessful(feedClient);
        assertFeedSuccessful(feedClient); // ensure the client can be reused
        feedClient.close();
    }

    private void assertFeedSuccessful(SyncFeedClient feedClient) {
        List<SyncOperation> operations = new ArrayList<>();

        operations.add(new SyncOperation("id::test::1",
                                         "{" +
                                                       "    \"put\": \"id::test::1\"," +
                                                       "    \"fields\": {" +
                                                       "        \"title\": \"Title 1\"" +
                                                       "    }" +
                                                       "}"));
        operations.add(new SyncOperation("id::test::2",
                                         "{" +
                                         "    \"put\": \"id::test::2\"," +
                                         "    \"fields\": {" +
                                         "        \"title\": \"Title 2\"" +
                                         "    }" +
                                         "}"));
        operations.add(new SyncOperation("id::test::3",
                                         "{" +
                                         "    \"put\": \"id::test::3\"," +
                                         "    \"fields\": {" +
                                         "        \"title\": \"Title 3\"" +
                                         "    }" +
                                         "}"));
        operations.add(new SyncOperation("id::test::3", // Another operation for the same document
                                         "{" +
                                         "    \"put\": \"id::test::3\"," +
                                         "    \"fields\": {" +
                                         "        \"title\": \"Title 4\"" +
                                         "    }" +
                                         "}"));

        SyncResult result = feedClient.stream(operations);

        assertTrue(result.isSuccess());
        assertEquals(4, result.results().size());
        assertNull(result.exception());
        assertEquals("id::test::1", result.results().get(0).getDocumentId());
        assertEquals("id::test::2", result.results().get(1).getDocumentId());
        assertEquals("id::test::3", result.results().get(2).getDocumentId());
        assertEquals("id::test::3", result.results().get(3).getDocumentId());
    }

}
