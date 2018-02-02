// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.config;

import com.google.common.annotations.Beta;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import net.jcip.annotations.Immutable;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Parameters given to a {@link com.yahoo.vespa.http.client.SessionFactory}
 * when creating {@link com.yahoo.vespa.http.client.Session}s. The parameters
 * contained in this class are related to the connections from the node running
 * the Session to the Vespa clusters.
 * This class is immutable
 * and has no public constructor - to instantiate one, use a {@link Builder}.
 *
 * @author Einar M R Rosenvinge
 */
@Immutable
public final class ConnectionParams {

    /**
     * Builder for {@link ConnectionParams}.
     */
    public static final class Builder {
        private SSLContext sslContext = null;
        private HostnameVerifier hostnameVerifier = SSLConnectionSocketFactory.getDefaultHostnameVerifier();
        private long connectionTimeout = TimeUnit.SECONDS.toMillis(60);
        private final Multimap<String, String> headers = ArrayListMultimap.create();
        private final Map<String, HeaderProvider> headerProviders = new HashMap<>();
        private int numPersistentConnectionsPerEndpoint = 8;
        private String proxyHost = null;
        private int proxyPort = 8080;
        private boolean useCompression = false;
        private int maxRetries = 100;
        private long minTimeBetweenRetriesMs = 700;
        private boolean dryRun = false;
        private boolean enableV3Protocol = true;
        private int traceLevel = 0;
        private int traceEveryXOperation = 0;
        private boolean printTraceToStdErr = true;

        /**
         * Sets the SSLContext for the connection to the gateway when SSL is enabled for Endpoint.
         * Default null (no ssl). See also Endpoint configuration.
         *
         * @param sslContext sslContext for connection to gateway.
         * @return pointer to builder.
         */
        public Builder setSslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /**
         * Sets the {@link HostnameVerifier} for the connection to the gateway when SSL is enabled for Endpoint.
         * Defaults to instance returned by {@link SSLConnectionSocketFactory#getDefaultHostnameVerifier()}.
         *
         * @param hostnameVerifier hostname verifier for connection to gateway.
         * @return pointer to builder.
         */
        public Builder setHostnameVerifier(HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;
            return this;
        }

        /**
         * Set custom headers to be used
         *
         * @param key header name
         * @param value header value
         * @return pointer to builder.
         */
        public Builder addHeader(String key, String value) {
            headers.put(key, value);
            return this;
        }

        /**
         * Adds a header provider for dynamic headers; headers where the value may change during a feeding session
         * (e.g. security tokens with limited life time). Only one {@link HeaderProvider} is allowed for a given header name.
         *
         * @param provider A provider for a dynamic header
         * @return pointer to builder.
         * @throws IllegalArgumentException if a provider is already registered for the given header name
         */
        public Builder addDynamicHeader(String headerName, HeaderProvider provider) {
            Objects.requireNonNull(headerName, "Header name cannot be null");
            Objects.requireNonNull(provider, "Header provider cannot be null");
            if (headerProviders.containsKey(headerName)) {
                throw new IllegalArgumentException("Provider already registered for name '" + headerName + "'");
            }
            headerProviders.put(headerName, provider);
            return this;
        }

        /**
         * The number of connections between the http client and the gateways. A very low number can result
         * in the network not fully utilized and the round-trip time can be a limiting factor. A low number
         * can cause skew in distribution of load between gateways. A too high number will cause
         * many threads to run, more context switching and potential more memory usage. We recommend using about
         * 16 connections per gateway.
         *
         * @param numPersistentConnectionsPerEndpoint number of channels per endpoint
         * @return pointer to builder.
         */
        public Builder setNumPersistentConnectionsPerEndpoint(int numPersistentConnectionsPerEndpoint) {
            this.numPersistentConnectionsPerEndpoint = numPersistentConnectionsPerEndpoint;
            return this;
        }

        /**
         * Sets the HTTP proxy host name to use.
         *
         * @param proxyHost host name for proxy.
         * @return pointer to builder.
         */
        public Builder setProxyHost(String proxyHost) {
            this.proxyHost = proxyHost;
            return this;
        }

        /**
         * Sets the HTTP proxy host port to use.
         *
         * @param proxyPort host port for proxy.
         * @return pointer to builder.
         */
        public Builder setProxyPort(int proxyPort) {
            this.proxyPort = proxyPort;
            return this;
        }

        /**
         * Set whether compression of document operations during communication to server should be enabled.
         *
         * @param useCompression true if compression should be enabled.
         * @return pointer to builder.
         */
        public Builder setUseCompression(boolean useCompression) {
            this.useCompression = useCompression;
            return this;
        }

        /**
         * Set how many times to retry sending an operation to a gateway when encountering transient problems.
         *
         * @param maxRetries max number of retries
         * @return pointer to builder.
         */
        public Builder setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Don't send data to gateway, just pretend that everything is fine.
         *
         * @param dryRun true if enabled.
         * @return pointer to builder.
         */
        public Builder setDryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        @Beta
        public Builder setEnableV3Protocol(boolean enableV3Protocol) {
            this.enableV3Protocol = enableV3Protocol;
            return this;
        }

        /**
         * Set the min time between retries when temporarily failing against a gateway.
         *
         * @param minTimeBetweenRetries the min time value
         * @param unit                  the unit of the min time.
         * @return pointer to builder.
         */
        public Builder setMinTimeBetweenRetries(long minTimeBetweenRetries, TimeUnit unit) {
            this.minTimeBetweenRetriesMs = unit.toMillis(minTimeBetweenRetries);
            return this;
        }

        public long getMinTimeBetweenRetriesMs() {
            return minTimeBetweenRetriesMs;
        }

        /**
         * Sets the trace level for tracing messagebus. 0 means to tracing.
         *
         * @param traceLevel tracelevel, larger value means more tracing.
         * @return pointer to builder.
         */
        public Builder setTraceLevel(int traceLevel) {
            this.traceLevel = traceLevel;
            return this;
        }

        /**
         * How often to trace messages in client. Please note that this does not affect tracing with messagebus
         *
         * @param traceEveryXOperation if zero, no tracing, 1 = every message, and so on.
         * @return pointer to builder.
         */
        public Builder setTraceEveryXOperation(int traceEveryXOperation) {
            this.traceEveryXOperation = traceEveryXOperation;
            return this;
        }

        /**
         * If enabled will write internal trace to stderr.
         *
         * @param printTraceToStdErr if value is true it is enabled.
         * @return pointer to builder.
         */
        public Builder setPrintTraceToStdErr(boolean printTraceToStdErr) {
            this.printTraceToStdErr = printTraceToStdErr;
            return this;
        }


        public ConnectionParams build() {
            return new ConnectionParams(
                    sslContext,
                    hostnameVerifier,
                    connectionTimeout,
                    headers,
                    headerProviders,
                    numPersistentConnectionsPerEndpoint,
                    proxyHost,
                    proxyPort,
                    useCompression,
                    maxRetries,
                    minTimeBetweenRetriesMs,
                    dryRun,
                    enableV3Protocol,
                    traceLevel,
                    traceEveryXOperation,
                    printTraceToStdErr);
        }

        public int getNumPersistentConnectionsPerEndpoint() {
            return numPersistentConnectionsPerEndpoint;
        }

        public String getProxyHost() {
            return proxyHost;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public int getMaxRetries() {
            return maxRetries;
        }
        public int getTraceLevel() {
            return traceLevel;
        }
        public int getTraceEveryXOperation() {
            return traceEveryXOperation;
        }

        public boolean getPrintTraceToStdErr() {
            return printTraceToStdErr;
        }

        public int getProxyPort() {
            return proxyPort;
        }

        public SSLContext getSslContext() {
            return sslContext;
        }

        public HostnameVerifier getHostnameVerifier() {
            return hostnameVerifier;
        }
    }
    private final SSLContext sslContext;
    private final HostnameVerifier hostnameVerifier;
    private final long connectionTimeout;
    private final Multimap<String, String> headers = ArrayListMultimap.create();
    private final Map<String, HeaderProvider> headerProviders = new HashMap<>();
    private final int numPersistentConnectionsPerEndpoint;
    private final String proxyHost;
    private final int proxyPort;
    private final boolean useCompression;
    private final int maxRetries;
    private final long minTimeBetweenRetriesMs;
    private final boolean dryRun;
    private final boolean enableV3Protocol;
    private final int traceLevel;
    private final int traceEveryXOperation;
    private final boolean printTraceToStdErr;

    private ConnectionParams(
            SSLContext sslContext,
            HostnameVerifier hostnameVerifier,
            long connectionTimeout,
            Multimap<String, String> headers,
            Map<String, HeaderProvider> headerProviders,
            int numPersistentConnectionsPerEndpoint,
            String proxyHost,
            int proxyPort,
            boolean useCompression,
            int maxRetries,
            long minTimeBetweenRetriesMs,
            boolean dryRun,
            boolean enableV3Protocol,
            int traceLevel,
            int traceEveryXOperation,
            boolean printTraceToStdErr) {
        this.sslContext = sslContext;
        this.hostnameVerifier = hostnameVerifier;
        this.connectionTimeout = connectionTimeout;
        this.headers.putAll(headers);
        this.headerProviders.putAll(headerProviders);
        this.numPersistentConnectionsPerEndpoint = numPersistentConnectionsPerEndpoint;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.useCompression = useCompression;
        this.maxRetries = maxRetries;
        this.minTimeBetweenRetriesMs = minTimeBetweenRetriesMs;
        this.dryRun = dryRun;
        this.enableV3Protocol = enableV3Protocol;
        this.traceLevel = traceLevel;
        this.traceEveryXOperation = traceEveryXOperation;
        this.printTraceToStdErr = printTraceToStdErr;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    public Collection<Map.Entry<String, String>> getHeaders() {
        return Collections.unmodifiableCollection(headers.entries());
    }

    public Map<String, HeaderProvider> getDynamicHeaders() {
        return Collections.unmodifiableMap(headerProviders);
    }

    public int getNumPersistentConnectionsPerEndpoint() {
        return numPersistentConnectionsPerEndpoint;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public boolean getUseCompression() {
        return useCompression;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getMinTimeBetweenRetriesMs() {
        return minTimeBetweenRetriesMs;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isEnableV3Protocol() { return enableV3Protocol; }

    public int getTraceLevel() {
        return traceLevel;
    }

    public int getTraceEveryXOperation() {
        return traceEveryXOperation;
    }

    public boolean getPrintTraceToStdErr() {
        return printTraceToStdErr;
    }

    /**
     * A header provider that provides a header value. {@link #getHeaderValue()} is called each time a new HTTP request
     * is constructed by {@link com.yahoo.vespa.http.client.FeedClient}.
     *
     * Important: The implementation of {@link #getHeaderValue()} must be thread-safe!
     */
    public interface HeaderProvider {
        String getHeaderValue();
    }

}
