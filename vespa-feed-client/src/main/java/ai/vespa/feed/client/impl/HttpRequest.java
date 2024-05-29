// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import java.time.Duration;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

class HttpRequest {

    private final String method;
    private final String path;
    private final String query;
    private final Map<String, Supplier<String>> headers;
    private final byte[] body;
    private final Duration timeout;
    private final long deadlineNanos;
    private final LongSupplier nanoClock;

    public HttpRequest(String method, String path, String query, Map<String, Supplier<String>> headers, byte[] body, Duration timeout, LongSupplier nanoClock) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.headers = headers;
        this.body = body;
        this.deadlineNanos = nanoClock.getAsLong() + timeout.toNanos();
        this.timeout = timeout;
        this.nanoClock = nanoClock;
    }

    public String method() {
        return method;
    }

    public String pathAndQuery() {
        return path + (query.isEmpty() ? "?" : query + "&") + "timeout=" + Math.max(1, timeLeft().toMillis()) + "ms";
    }

    public Map<String, Supplier<String>> headers() {
        return headers;
    }

    public byte[] body() {
        return body;
    }

    public Duration timeLeft() {
        return Duration.ofNanos(deadlineNanos - nanoClock.getAsLong());
    }

    public Duration timeout() {
        return timeout;
    }

    @Override
    public String toString() {
        return method + " " + path;
    }

}
