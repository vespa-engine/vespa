// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

public interface HttpResponse {

    int code();
    byte[] body();

    static HttpResponse of(int code, byte[] body) {
        return new HttpResponse() {
            @Override public int code() { return code; }
            @Override public byte[] body() { return body; }
        };
    }

}
