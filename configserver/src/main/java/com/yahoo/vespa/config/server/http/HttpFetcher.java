// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;

import java.net.URI;
import java.net.URL;

public interface HttpFetcher {

    class Params {
        // See HttpUrlConnection::setReadTimeout. 0 means infinite (not recommended!).
        public final int readTimeoutMs;

        public Params(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    // On failure to get or build HttpResponse for url, an exception is thrown to be handled by HttpHandler.
    HttpResponse get(Params params, URI url);

}
