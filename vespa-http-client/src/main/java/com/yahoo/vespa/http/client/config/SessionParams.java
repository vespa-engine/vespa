// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Parameters given to a {@link com.yahoo.vespa.http.client.FeedClientFactory}
 * when creating {@link com.yahoo.vespa.http.client.FeedClient}s. This class is immutable
 * and has no public constructor - to instantiate one, use a {@link Builder}.
 *
 * @author Einar M R Rosenvinge
 * @see com.yahoo.vespa.http.client.FeedClientFactory
 * @see Builder
 */
public final class SessionParams {

    /**
     * Interface for handling serious errors with connection.
     */
    public interface ErrorReporter {
        void onSessionError(Endpoint endpoint, String oldSessionID, String newSessionId);
    }

    /**
     * Mutable class used to instantiate a {@link SessionParams}. A builder
     * instance will at the very least contain cluster settings (
     * {@link #addCluster(Cluster)}), for supporting SSL and similar transport
     * settings, use {@link #setConnectionParams(ConnectionParams)}.
     */
    public static final class Builder {

        private final List<Cluster> clusters = new LinkedList<>();
        private FeedParams feedParams = new FeedParams.Builder().build();
        private ConnectionParams connectionParams = new ConnectionParams.Builder().build();
        private int clientQueueSize = 10000;
        private ErrorReporter errorReporter = null;
        private int throttlerMinSize = 0;

        /**
         * Add a Vespa installation for feeding documents into.
         *
         * @return this Builder instance, to support chaining
         */
        public Builder addCluster(Cluster cluster) {
            clusters.add(cluster);
            return this;
        }

        /**
         * Set parameters used for feeding the documents in the receiving
         * cluster. Reasonable defaults are supplied, so setting this should not
         * be necessary for testing.
         *
         * @return this builder instance to support chaining
         */
        public Builder setFeedParams(FeedParams feedParams) {
            this.feedParams = feedParams;
            return this;
        }

        /**
         * Transport parameters, like custom HTTP headers.
         *
         * @return this Builder instance, to support chaining
         */
        public Builder setConnectionParams(ConnectionParams connectionParams) {
            this.connectionParams = connectionParams;
            return this;
        }

        /**
         * Sets an error reporter that is invoked in case of serious errors.
         *
         * @param errorReporter the handler
         * @return pointer to builder.
         */
        public Builder setErrorReporter(ErrorReporter errorReporter) {
            this.errorReporter = errorReporter;
            return this;
        }

        /**
         * Sets the maximum number of document operations to hold in memory, waiting to be
         * sent to Vespa. When this threshold is reached, {@link java.io.OutputStream#close()} will block.
         *
         * @param clientQueueSize the maximum number of document operations to hold in memory.
         * @return pointer to builder.
         */
        public Builder setClientQueueSize(int clientQueueSize) {
            this.clientQueueSize = clientQueueSize;
            return this;
        }

        /**
         * Sets the minimum queue size of the throttler. If this is zero, it means that dynamic throttling is
         * not enabled. Otherwise it is the minimum size of the throttler for how many parallel requests that are
         * accepted. The max size of the throttler is the clientQueueSize.
         * @return the minimum number of requests to be used in throttler or zero if throttler is static.
         *
         * @param throttlerMinSize the value of the min size.
         */
        public Builder setThrottlerMinSize(int throttlerMinSize) {
            this.throttlerMinSize = throttlerMinSize;
            return this;
        }

        /**
         * Instantiates a {@link SessionParams} that can be given to a {@link com.yahoo.vespa.http.client.FeedClientFactory}.
         *
         * @return a SessionParams object with the parameters of this Builder
         */
        public SessionParams build() {
            return new SessionParams(
                    clusters, feedParams, connectionParams, clientQueueSize, errorReporter, throttlerMinSize);
        }

        public FeedParams getFeedParams() {
            return feedParams;
        }
        public ConnectionParams getConnectionParams() {
            return connectionParams;
        }
        public int getClientQueueSize() {
            return clientQueueSize;
        }
        public int getThrottlerMinSize() {
            return throttlerMinSize;
        }
    }

    // NOTE! See toBuilder at the end of this class if you add fields here

    private final List<Cluster> clusters;
    private final FeedParams feedParams;
    private final ConnectionParams connectionParams;
    private final int clientQueueSize;
    private final ErrorReporter errorReport;
    private final int throttlerMinSize;

    private SessionParams(Collection<Cluster> clusters,
                          FeedParams feedParams,
                          ConnectionParams connectionParams,
                          int clientQueueSize,
                          ErrorReporter errorReporter,
                          int throttlerMinSize) {
        this.clusters = Collections.unmodifiableList(new ArrayList<>(clusters));
        this.feedParams = feedParams;
        this.connectionParams = connectionParams;
        this.clientQueueSize = clientQueueSize;
        this.errorReport = errorReporter;
        this.throttlerMinSize = throttlerMinSize;
    }

    public List<Cluster> getClusters() {
        return clusters;
    }

    public FeedParams getFeedParams() {
        return feedParams;
    }

    public ConnectionParams getConnectionParams() {
        return connectionParams;
    }

    public int getClientQueueSize() {
        return clientQueueSize;
    }

    public int getThrottlerMinSize() {
        return throttlerMinSize;
    }

    public ErrorReporter getErrorReport() {
        return errorReport;
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        clusters.forEach(c -> b.addCluster(c));
        b.setFeedParams(feedParams);
        b.setConnectionParams(connectionParams);
        b.setClientQueueSize(clientQueueSize);
        b.setErrorReporter(errorReport);
        b.setThrottlerMinSize(throttlerMinSize);
        return b;
    }

}
