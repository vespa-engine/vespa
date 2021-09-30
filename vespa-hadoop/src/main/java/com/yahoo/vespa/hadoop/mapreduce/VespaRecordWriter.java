// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.mapreduce;

import ai.vespa.feed.client.FeedClient;
import ai.vespa.feed.client.FeedClientBuilder;
import ai.vespa.feed.client.JsonFeeder;
import ai.vespa.feed.client.OperationParseException;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaConfiguration;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaCounters;
import com.yahoo.vespa.http.client.config.FeedParams;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;

/**
 * {@link VespaRecordWriter} sends the output &lt;key, value&gt; to one or more Vespa endpoints using vespa-feed-client.
 *
 * @author bjorncs
 */
public class VespaRecordWriter extends RecordWriter<Object, Object> {

    private final static Logger log = Logger.getLogger(VespaRecordWriter.class.getCanonicalName());

    private final VespaCounters counters;
    private final VespaConfiguration config;

    private boolean initialized = false;
    private JsonFeeder feeder;

    protected VespaRecordWriter(VespaConfiguration config, VespaCounters counters) {
        this.counters = counters;
        this.config = config;
    }

    @Override
    public void write(Object key, Object data) throws IOException {
        initializeOnFirstWrite();
        String json = data.toString().trim();
        feeder.feedSingle(json)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        if (error instanceof OperationParseException) {
                            counters.incrementDocumentsSkipped(1);
                        } else {
                            String msg = "Failed to feed single document: " + error;
                            log.log(Level.WARNING, msg, error);
                            counters.incrementDocumentsFailed(1);
                        }
                    } else {
                        counters.incrementDocumentsOk(1);
                    }
                });
        counters.incrementDocumentsSent(1);
        if (counters.getDocumentsSent() % config.progressInterval() == 0) {
            String progress = String.format("Feed progress: %d / %d / %d / %d (sent, ok, failed, skipped)",
                    counters.getDocumentsSent(),
                    counters.getDocumentsOk(),
                    counters.getDocumentsFailed(),
                    counters.getDocumentsSkipped());
            log.info(progress);
        }
    }

    @Override
    public void close(TaskAttemptContext context) throws IOException {
        if (feeder != null) {
            feeder.close();
            feeder = null;
            initialized = false;
        }
    }

    /** Override method to alter {@link FeedClient} configuration */
    protected void onFeedClientInitialization(FeedClientBuilder builder) {}

    private void initializeOnFirstWrite() {
        if (initialized) return;
        validateConfig();
        useRandomizedStartupDelayIfEnabled();
        feeder = createJsonStreamFeeder();
        initialized = true;
    }

    private void validateConfig() {
        if (config.dataFormat() != FeedParams.DataFormat.JSON_UTF8) {
            throw new IllegalArgumentException("Only JSON is support by this feed client implementation");
        }
    }

    private void useRandomizedStartupDelayIfEnabled() {
        if (!config.dryrun() && config.randomStartupSleepMs() > 0) {
            int delay = ThreadLocalRandom.current().nextInt(config.randomStartupSleepMs());
            log.info("Delaying startup by " + delay + " ms");
            try {
                Thread.sleep(delay);
            } catch (Exception e) {}
        }
    }


    private JsonFeeder createJsonStreamFeeder() {
        FeedClient feedClient = createFeedClient();
            JsonFeeder.Builder builder = JsonFeeder.builder(feedClient)
                .withTimeout(Duration.ofMinutes(10));
        if (config.route() != null) {
            builder.withRoute(config.route());
        }
        return builder.build();

    }

    private FeedClient createFeedClient() {
        List<URI> endpoints = endpointUris(config);
        log.info("Using endpoints " + endpoints);
        int streamsPerConnection = streamsPerConnection(config);
        log.log(Level.INFO, "Using {0} max streams per connection", new Object[] {streamsPerConnection});
        log.log(Level.INFO, "Using {0} connections", new Object[] {config.numConnections()});
        FeedClientBuilder feedClientBuilder = FeedClientBuilder.create(endpoints)
                .setConnectionsPerEndpoint(config.numConnections())
                .setMaxStreamPerConnection(streamsPerConnection)
                .setDryrun(config.dryrun())
                .setRetryStrategy(retryStrategy(config));

        onFeedClientInitialization(feedClientBuilder);
        return feedClientBuilder.build();
    }

    private static FeedClient.RetryStrategy retryStrategy(VespaConfiguration config) {
        int maxRetries = config.numRetries();
        return new FeedClient.RetryStrategy() {
            @Override public int retries() { return maxRetries; }
        };
    }

    private static int streamsPerConnection(VespaConfiguration config) {
        return Math.min(256, config.maxInFlightRequests() / config.numConnections());
    }

    private static List<URI> endpointUris(VespaConfiguration config) {
        return Arrays.stream(config.endpoint().split(","))
                .map(hostname -> URI.create(String.format("https://%s:%d/", hostname, config.defaultPort())))
                .collect(toList());
    }
}
