// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.client;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Builder for creating a {@link FeedClient} instance.
 *
 * @author bjorncs
 */
public class FeedClientBuilder {

    SSLContext sslContext;
    HostnameVerifier hostnameVerifier;
    final Map<String, Supplier<String>> requestHeaders = new HashMap<>();
    URI endpoint;
    Integer maxConnections;

    public static FeedClientBuilder create() { return new FeedClientBuilder(); }

    private FeedClientBuilder() {}

    public FeedClientBuilder setMaxConnection(int max) { this.maxConnections = max; return this; }

    public FeedClientBuilder setEndpoint(URI endpoint) { this.endpoint = endpoint; return this; }

    public FeedClientBuilder setSslContext(SSLContext context) { this.sslContext = context; return this; }

    public FeedClientBuilder setHostnameVerifier(HostnameVerifier verifier) { this.hostnameVerifier = verifier; return this; }

    public FeedClientBuilder addRequestHeader(String name, String value) { return addRequestHeader(name, () -> value); }

    public FeedClientBuilder addRequestHeader(String name, Supplier<String> valueSupplier) {
        this.requestHeaders.put(name, valueSupplier);
        return this;
    }

    public FeedClient build() { return new HttpFeedClient(this); }
}
