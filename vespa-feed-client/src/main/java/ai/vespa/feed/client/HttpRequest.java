// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.util.Map;
import java.util.function.Supplier;

class HttpRequest {

    private final String method;
    private final String path;
    private final Map<String, Supplier<String>> headers;
    private final byte[] body;

    public HttpRequest(String method, String path, Map<String, Supplier<String>> headers, byte[] body) {
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.body = body;
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

    @Override
    public String toString() {
        return method + " " + path;
    }

}
