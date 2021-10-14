// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.vespa.http.client.FeedClient;
import com.yahoo.vespa.http.client.FeedClientFactory;
import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.Server;
import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.FeedParams;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.handlers.V3MockParsingRequestHandler;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit test that test documentation code.
 *
 * @author dybis
 */
public class ExampleUsageFeedClientTest {

    @Test
    public void testExampleCode() {
        Server serverA =
                new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);
        Server serverB =
                new Server(new V3MockParsingRequestHandler(200, V3MockParsingRequestHandler.Scenario.ALL_OK), 0);

        exampleCode("localhost", serverA.getPort(), "localhost", serverB.getPort());
        serverA.close();
        serverB.close();
    }

    private static CharSequence generateDocument(String docId) {
        // Just a dummy example of an update document operation.
        return "{\"update\": \""+ docId + "\","
                + " \"fields\": { \"actualMapStringToArrayOfInt\": {"
                + " \"assign\": ["
                + "{ \"key\": \"fooKey\", \"value\": [ 2,1, 3] }"
                + "]}}}";
    }

    // Example usage of FeedClient
    public static void exampleCode(String hostNameA, int portServerA, String hostNameB, int portServerB) {
        boolean useSsl = false;
        final SessionParams sessionParams = new SessionParams.Builder()
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create(hostNameA, portServerA, useSsl)).build())
                .addCluster(new Cluster.Builder().addEndpoint(Endpoint.create(hostNameB, portServerB, useSsl)).build())
                .setFeedParams(new FeedParams.Builder()
                        .setDataFormat(FeedParams.DataFormat.JSON_UTF8)
                        .build())
                .build();

        AtomicInteger resultsReceived = new AtomicInteger(0);
        AtomicInteger errorsReceived = new AtomicInteger(0);

        FeedClient feedClient = FeedClientFactory.create(sessionParams, new FeedClient.ResultCallback() {
            @Override
            public void onCompletion(String docId, Result documentResult) {
                resultsReceived.incrementAndGet();
                if (! documentResult.getContext().equals(docId)) {
                    System.err.println("Context does not work as expected.");
                    errorsReceived.incrementAndGet();
                }
                if (!documentResult.isSuccess()) {
                    System.err.println("Problems with docID " + docId + ":" + documentResult.toString());
                    errorsReceived.incrementAndGet();
                }
            }
        });
        int sentCounter = 0;
        List<String> docIds = Arrays.asList("1", "2", "3", "4");
        for (final String docId : docIds) {
            CharSequence docData = generateDocument(docId);
            feedClient.stream(docId, docData, docId);
            sentCounter++;
        }
        feedClient.close();
    }

}
