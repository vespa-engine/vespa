// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.util.Map;

public interface HttpResponse {

    int code();
    byte[] body();

    default String contentType() { return "application/json"; }

    /** Returns the value of the given response header, or {@code null} if it is not present. */
    default String header(String name) { return null; }

    static HttpResponse of(int code, byte[] body) {
        return of(code, body, Map.of());
    }

    static HttpResponse of(int code, byte[] body, Map<String, String> headers) {
        return new HttpResponse() {
            @Override public int code() { return code; }
            @Override public byte[] body() { return body; }
            @Override public String header(String name) { return headers.get(name); }
        };
    }

}
