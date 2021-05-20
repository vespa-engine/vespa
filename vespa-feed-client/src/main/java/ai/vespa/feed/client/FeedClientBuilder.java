// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Builder for creating a {@link FeedClient} instance.
 *
 * @author bjorncs
 * @author jonmv
 */
public class FeedClientBuilder {

    FeedClient.RetryStrategy defaultRetryStrategy = new FeedClient.RetryStrategy() { };

    final URI endpoint;
    final Map<String, Supplier<String>> requestHeaders = new HashMap<>();
    SSLContext sslContext;
    HostnameVerifier hostnameVerifier;
    int maxConnections = 4;
    int maxStreamsPerConnection = 1024;
    FeedClient.RetryStrategy retryStrategy = defaultRetryStrategy;

    public static FeedClientBuilder create(URI endpoint) { return new FeedClientBuilder(endpoint); }

    private FeedClientBuilder(URI endpoint) {
        requireNonNull(endpoint.getHost());
        this.endpoint = endpoint;
    }

    /**
     * Sets the maximum number of connections this client will use.
     *
     * A reasonable value here is a small multiple of the numbers of containers in the
     * cluster to feed, so load can be balanced across these.
     * In general, this value should be kept as low as possible, but poor connectivity
     * between feeder and cluster may also warrant a higher number of connections.
     */
    public FeedClientBuilder setMaxConnections(int max) {
        if (max < 1) throw new IllegalArgumentException("Max connections must be at least 1, but was " + max);
        this.maxConnections = max;
        return this;
    }

    /**
     * Sets the maximum number of streams per HTTP/2 connection for this client.
     *
     * This determines the maximum number of concurrent, inflight requests for this client,
     * which is {@code maxConnections * maxStreamsPerConnection}. Prefer more streams over
     * more connections, when possible. The server's maximum is usually around 128-256.
     */
    public FeedClientBuilder setMaxStreamPerConnection(int max) {
        if (max < 1) throw new IllegalArgumentException("Max streams per connection must be at least 1, but was " + max);
        this.maxStreamsPerConnection = max;
        return this;
    }

    public FeedClientBuilder setSslContext(SSLContext context) {
        this.sslContext = requireNonNull(context);
        return this;
    }

    public FeedClientBuilder setHostnameVerifier(HostnameVerifier verifier) {
        this.hostnameVerifier = requireNonNull(verifier);
        return this;
    }

    public FeedClientBuilder addRequestHeader(String name, String value) {
        return addRequestHeader(name, () -> requireNonNull(value));
    }

    public FeedClientBuilder addRequestHeader(String name, Supplier<String> valueSupplier) {
        this.requestHeaders.put(requireNonNull(name), requireNonNull(valueSupplier));
        return this;
    }

    public FeedClientBuilder setRetryStrategy(FeedClient.RetryStrategy strategy) {
        this.retryStrategy = requireNonNull(strategy);
        return this;
    }

    public FeedClient build() {
        return new HttpFeedClient(this);
    }

}
