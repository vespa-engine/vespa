package com.yahoo.vespa.hadoop.mapreduce;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaConfiguration;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaCounters;
import com.yahoo.vespa.hadoop.pig.VespaDocumentOperation;
import com.yahoo.vespa.http.client.FeedClient;
import com.yahoo.vespa.http.client.FeedClientFactory;
import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.config.*;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import java.io.IOException;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Logger;

/**
 * VespaRecordWriter sends the output &lt;key, value&gt; to one or more Vespa
 * endpoints.
 *
 * @author lesters
 */
public class VespaRecordWriter extends RecordWriter {

    private final static Logger log = Logger.getLogger(VespaRecordWriter.class.getCanonicalName());

    private boolean initialized = false;
    private FeedClient feedClient;

    private final VespaCounters counters;
    private final VespaConfiguration configuration;
    private final int progressInterval;


    VespaRecordWriter(VespaConfiguration configuration, VespaCounters counters) {
        this.counters = counters;
        this.configuration = configuration;
        this.progressInterval = configuration.progressInterval();
    }


    @Override
    public void write(Object key, Object data) throws IOException, InterruptedException {
        if (!initialized) {
            initialize();
        }

        // Assumption: json - xml not currently supported
        String json = data.toString().trim();

        // Parse json to find document id - if none found, skip this write
        String docId = findDocId(json);
        if (docId != null && docId.length() >= 0) {
            feedClient.stream(docId, json);
            counters.incrementDocumentsSent(1);
        } else {
            counters.incrementDocumentsSkipped(1);
        }

        if (counters.getDocumentsSent() % progressInterval == 0) {
            String progress = String.format("Feed progress: %d / %d / %d / %d (sent, ok, failed, skipped)",
                    counters.getDocumentsSent(),
                    counters.getDocumentsOk(),
                    counters.getDocumentsFailed(),
                    counters.getDocumentsSkipped());
            log.info(progress);
        }

    }


    @Override
    public void close(TaskAttemptContext taskAttemptContext) throws IOException, InterruptedException {
        if (feedClient != null) {
            feedClient.close();
        }
    }


    private void initialize() {
        ConnectionParams.Builder connParamsBuilder = new ConnectionParams.Builder();
        connParamsBuilder.setDryRun(configuration.dryrun());
        connParamsBuilder.setUseCompression(configuration.useCompression());
        connParamsBuilder.setEnableV3Protocol(configuration.useV3Protocol());
        connParamsBuilder.setNumPersistentConnectionsPerEndpoint(configuration.numConnections());
        if (configuration.proxyHost() != null) {
            connParamsBuilder.setProxyHost(configuration.proxyHost());
        }
        if (configuration.proxyPort() >= 0) {
            connParamsBuilder.setProxyPort(configuration.proxyPort());
        }

        SessionParams.Builder sessionParams = new SessionParams.Builder();
        sessionParams.setThrottlerMinSize(configuration.throttlerMinSize());
        sessionParams.setConnectionParams(connParamsBuilder.build());
        sessionParams.setFeedParams(new FeedParams.Builder()
                .setDataFormat(configuration.dataFormat())
                .build());

        String endpoints = configuration.endpoint();
        StringTokenizer tokenizer = new StringTokenizer(endpoints, ",");
        while (tokenizer.hasMoreTokens()) {
            String endpoint = tokenizer.nextToken().trim();
            sessionParams.addCluster(new Cluster.Builder().addEndpoint(
                    Endpoint.create(endpoint, 4080, false)
            ).build());
        }

        ResultCallback resultCallback = new ResultCallback(counters);
        feedClient = FeedClientFactory.create(sessionParams.build(), resultCallback);

        initialized = true;
    }


    private String findDocId(String json) throws IOException {
        JsonFactory factory = new JsonFactory();
        try(JsonParser parser = factory.createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return null;
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.getCurrentName();
                parser.nextToken();
                if (VespaDocumentOperation.Operation.valid(fieldName)) {
                    String docId = parser.getText();
                    return docId;
                } else {
                    parser.skipChildren();
                }
            }
        } catch (JsonParseException ex) {
            return null;
        }
        return null;
    }


    static class ResultCallback implements FeedClient.ResultCallback {
        final VespaCounters counters;

        public ResultCallback(VespaCounters counters) {
            this.counters = counters;
        }

        @Override
        public void onCompletion(String docId, Result documentResult) {
            if (!documentResult.isSuccess()) {
                counters.incrementDocumentsFailed(1);
                StringBuilder sb = new StringBuilder();
                sb.append("Problems with docid ");
                sb.append(docId);
                sb.append(": ");
                List<Result.Detail> details = documentResult.getDetails();
                for (Result.Detail detail : details) {
                    sb.append(detail.toString());
                    sb.append(" ");
                }
                log.warning(sb.toString());
                return;
            }
            counters.incrementDocumentsOk(1);
        }

    }

}
