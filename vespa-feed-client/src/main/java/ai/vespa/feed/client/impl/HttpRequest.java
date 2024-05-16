// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import java.time.Duration;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

class HttpRequest {

    private final String method;
    private final String path;
    private final Map<String, Supplier<String>> headers;
    private final byte[] body;
    private final long deadlineMillis;
    private final LongSupplier clock;

    public HttpRequest(String method, String path, Map<String, Supplier<String>> headers, byte[] body, Duration timeout, LongSupplier clock) {
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.body = body;
        this.deadlineMillis = clock.getAsLong() + timeout.toMillis();
        this.clock = clock;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public Map<String, Supplier<String>> headers() {
        return headers;
    }

    public byte[] body() {
        return body;
    }

    public Duration timeLeft() {
        return Duration.ofMillis(deadlineMillis - clock.getAsLong());
    }

    @Override
    public String toString() {
        return method + " " + path;
    }

}
