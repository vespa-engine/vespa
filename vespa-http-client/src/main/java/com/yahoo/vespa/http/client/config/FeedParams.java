// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.config;

import com.google.common.annotations.Beta;

import java.util.concurrent.TimeUnit;

/**
 * Feed level parameters. This class is immutable
 * and has no public constructor - to instantiate one, use a {@link Builder}.

 * @author Einar M R Rosenvinge
 */
public final class FeedParams {

    public boolean getDenyIfBusyV3() { return denyIfBusyV3; }

    public long getMaxSleepTimeMs() { return maxSleepTimeMs; }

    public boolean getSilentUpgrade() { return silentUpgrade; }

    /**
     * Enumeration of data formats that are acceptable by the
     * {@link com.yahoo.vespa.http.client.FeedClient} methods.
     */
    public enum DataFormat {
        /** UTF-8-encoded XML. Preamble is not necessary. */
        XML_UTF8,
        JSON_UTF8
    }
    /**
     * Mutable class used to instantiate a {@link FeedParams}.
     */
    public static final class Builder {

        private DataFormat dataFormat = DataFormat.JSON_UTF8;
        private long serverTimeout = TimeUnit.SECONDS.toMillis(180);
        private long clientTimeout = TimeUnit.SECONDS.toMillis(20);
        private String route = null;
        private int maxChunkSizeBytes = 50 * 1024;
        private int maxInFlightRequests = 5000;
        private long localQueueTimeOut = 180000;
        private String priority = null;
        private boolean denyIfBusyV3 = true;
        private long maxSleepTimeMs = 3000;
        private boolean silentUpgrade = true;
        private Double idlePollFrequency = null;

        /**
         * Make server not throw 4xx/5xx for situations that are normal during upgrade as this can esily mask
         * other problems. This feature need to be supported on server side to work, but it is still safe
         * to enable it, even if server does not yet support it. As of Nov 22 2016 it is not yet implemented on
         * the server side.
         * @param silentUpgrade true for reducing "false" 4xx/5xx.
         * @return this, for chaining
         */
        @Beta
        public Builder setSilentUpgrade(boolean silentUpgrade) {
            this.silentUpgrade = silentUpgrade;
            return this;
        }

        /**
         * When throttling the load due to transient errors on gateway, what is the most time to wait between
         * requests per thread. Only active for V3 protocol.
         * @param ms max with time
         * @return this, for chaining
         */
        public Builder setMaxSleepTimeMs(long ms) {
            this.maxSleepTimeMs = ms;
            return this;
        }

        /**
         * If this is set to false, the gateway will block threads until messagebus can send the message.
         * If true, the gateway will exit and fail the request early if there are many threads already
         * blocked.
         * @param value true to reduce number of blocked threads in gateway.
         * @return this, for chaining
         */
        public Builder setDenyIfBusyV3(boolean value) {
            denyIfBusyV3 = value;
            return this;
        }

        /**
         * Sets the data format to be used.
         *
         * @param dataFormat the data format to be used.
         * @see DataFormat
         * @return this, for chaining
         */
        public Builder setDataFormat(DataFormat dataFormat) {
            this.dataFormat = dataFormat;
            return this;
        }

        /**
         * Sets a route to be used for all Clusters, unless overridden on a per-cluster basis
         * in {@link com.yahoo.vespa.http.client.config.Cluster#getRoute()}.
         *
         * @param route a route to be used for all Clusters.
         * @return this, for chaining
         */
        public Builder setRoute(String route) {
            this.route = route;
            return this;
        }

        /**
         * Sets the server-side timeout of each operation - i.e. the timeout used by
         * the server endpoint for operations going over the message bus protocol into
         * Vespa.
         *
         * Note that the TOTAL timeout of any one operation in this API would be
         * {@link #getServerTimeout(java.util.concurrent.TimeUnit)} +
         * {@link #getClientTimeout(java.util.concurrent.TimeUnit)}.
         *
         * @param serverTimeout timeout value
         * @param unit unit of timeout value
         * @return this, for chaining
         */
        public Builder setServerTimeout(long serverTimeout, TimeUnit unit) {
            if (serverTimeout <= 0L) {
                throw new IllegalArgumentException("Server timeout cannot be zero or negative.");
            }
            this.serverTimeout = unit.toMillis(serverTimeout);
            return this;
        }

        /**
         * Sets the client-side timeout for each operation.&nbsp;If BOTH the server-side
         * timeout AND this timeout has passed, the {@link com.yahoo.vespa.http.client.FeedClient}
         * will synthesize a {@link com.yahoo.vespa.http.client.Result}.
         *
         * Note that the TOTAL timeout of any one operation in this API would be
         * {@link #getServerTimeout(java.util.concurrent.TimeUnit)} +
         * {@link #getClientTimeout(java.util.concurrent.TimeUnit)},
         * after which a result callback is guaranteed to be made.
         *
         * @param clientTimeout timeout value
         * @param unit unit of timeout value
         * @return this, for chaining
         */
        public Builder setClientTimeout(long clientTimeout, TimeUnit unit) {
            if (clientTimeout <= 0L) {
                throw new IllegalArgumentException("Client timeout cannot be zero or negative.");
            }
            this.clientTimeout = unit.toMillis(clientTimeout);
            return this;
        }

        /**
         * Sets the maximum number of bytes of document data to send per HTTP request.
         *
         * @param maxChunkSizeBytes max number of bytes per HTTP request.
         * @return this, for chaining
         */
        public Builder setMaxChunkSizeBytes(int maxChunkSizeBytes) {
            this.maxChunkSizeBytes = maxChunkSizeBytes;
            return this;
        }

        /**
         * Sets the maximum number of operations to be in-flight.
         *
         * @param maxInFlightRequests max number of operations.
         * @return this, for chaining
         */
        public Builder setMaxInFlightRequests(int maxInFlightRequests) {
            this.maxInFlightRequests = maxInFlightRequests;
            return this;
        }

        /**
         * Sets the number of milliseconds until we respond with a timeout for a document operation
         * if we still have not received a response.
         */
        public Builder setLocalQueueTimeOut(long timeOutMs) {
            this.localQueueTimeOut = timeOutMs;
            return this;
        }

        /**
         * Set what frequency to poll for async responses. Default is 10hz (every 0.1s), but 1000hz  when using SyncFeedClient
         */
        @Beta
        public Builder setIdlePollFrequency(Double idlePollFrequency) {
            this.idlePollFrequency = idlePollFrequency;
            return this;
        }

        /**
         * Sets the messagebus priority. The allowed values are HIGHEST, VERY_HIGH, HIGH_[1-3],
         * NORMAL_[1-6], LOW_[1-3], VERY_LOW, and LOWEST..
         * @param priority messagebus priority of this message.
         * @return this, for chaining
         */
        public Builder setPriority(String priority) {
            if (priority == null) {
                return this;
            }
            switch (priority) {
                case "HIGHEST":
                case "VERY_HIGH":
                case "HIGH_1":
                case "HIGH_2":
                case "HIGH_3":
                case "NORMAL_1":
                case "NORMAL_2":
                case "NORMAL_3":
                case "NORMAL_4":
                case "NORMAL_5":
                case "NORMAL_6":
                case "LOW_1":
                case "LOW_2":
                case "LOW_3":
                case "VERY_LOW":
                case "LOWEST":
                    this.priority = priority;
                    return this;
                default:
                    throw new IllegalArgumentException("Unknown value for priority: " + priority
                            + " Allowed values are HIGHEST, VERY_HIGH, HIGH_[1-3], " +
                            "NORMAL_[1-6], LOW_[1-3], VERY_LOW, and LOWEST.");
            }
        }

        /**
         * Instantiates a {@link FeedParams}.
         *
         * @return a FeedParams object with the parameters of this Builder
         */
        public FeedParams build() {
            return new FeedParams(
                    dataFormat, serverTimeout, clientTimeout, route,
                    maxChunkSizeBytes, maxInFlightRequests, localQueueTimeOut, priority,
                    denyIfBusyV3, maxSleepTimeMs, silentUpgrade, idlePollFrequency);
        }

        public long getClientTimeout(TimeUnit unit) {
            return unit.convert(clientTimeout, TimeUnit.MILLISECONDS);
        }

        public long getServerTimeout(TimeUnit unit) {
            return unit.convert(serverTimeout, TimeUnit.MILLISECONDS);
        }

        public String getRoute() {
            return route;
        }

        public DataFormat getDataFormat() {
            return dataFormat;
        }

        public int getMaxChunkSizeBytes() {
            return maxChunkSizeBytes;
        }

        public int getMaxInFlightRequests() {
            return maxInFlightRequests;
        }

    }

    // NOTE! See toBuilder at the end of this class if you add fields here

    private final DataFormat dataFormat;
    private final long serverTimeoutMillis;
    private final long clientTimeoutMillis;
    private final String route;
    private final int maxChunkSizeBytes;
    private final int maxInFlightRequests;
    private final long localQueueTimeOut;
    private final String priority;
    private final boolean denyIfBusyV3;
    private final long maxSleepTimeMs;
    private final boolean silentUpgrade;
    private final Double idlePollFrequency;

    private FeedParams(DataFormat dataFormat, long serverTimeout, long clientTimeout, String route,
                       int maxChunkSizeBytes, final int maxInFlightRequests,
                       long localQueueTimeOut, String priority, boolean denyIfBusyV3, long maxSleepTimeMs,
                       boolean silentUpgrade, Double idlePollFrequency) {
        this.dataFormat = dataFormat;
        this.serverTimeoutMillis = serverTimeout;
        this.clientTimeoutMillis = clientTimeout;
        this.route = route;
        this.maxChunkSizeBytes = maxChunkSizeBytes;
        this.maxInFlightRequests =  maxInFlightRequests;
        this.localQueueTimeOut = localQueueTimeOut;
        this.priority = priority;
        this.denyIfBusyV3 = denyIfBusyV3;
        this.maxSleepTimeMs = maxSleepTimeMs;
        this.silentUpgrade = silentUpgrade;
        this.idlePollFrequency = idlePollFrequency;

    }

    public DataFormat getDataFormat() { return dataFormat; }
    public String getRoute() { return route; }
    public long getServerTimeout(TimeUnit unit) { return unit.convert(serverTimeoutMillis, TimeUnit.MILLISECONDS); }
    public long getClientTimeout(TimeUnit unit) { return unit.convert(clientTimeoutMillis, TimeUnit.MILLISECONDS); }

    public int getMaxChunkSizeBytes() { return maxChunkSizeBytes; }
    public String getPriority() { return priority; }

    public String toUriParameters() {
        StringBuilder b = new StringBuilder();
        b.append("&dataformat=").append(dataFormat.name());  //name in dataFormat enum obviously must be ascii
        return b.toString();
    }

    public int getMaxInFlightRequests() { return maxInFlightRequests; }
    public long getLocalQueueTimeOut() { return localQueueTimeOut; }
    public Double getIdlePollFrequency() { return idlePollFrequency; }

    /** Returns a builder initialized to the values of this */
    public FeedParams.Builder toBuilder() {
        Builder b = new Builder();
        b.setDataFormat(dataFormat);
        b.setServerTimeout(serverTimeoutMillis, TimeUnit.MILLISECONDS);
        b.setClientTimeout(clientTimeoutMillis, TimeUnit.MILLISECONDS);
        b.setRoute(route);
        b.setMaxChunkSizeBytes(maxChunkSizeBytes);
        b.setMaxInFlightRequests(maxInFlightRequests);
        b.setPriority(priority);
        b.setDenyIfBusyV3(denyIfBusyV3);
        b.setMaxSleepTimeMs(maxSleepTimeMs);
        b.setSilentUpgrade(silentUpgrade);
        b.setIdlePollFrequency(idlePollFrequency);
        return b;
    }

}
