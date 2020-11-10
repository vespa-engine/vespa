package com.yahoo.vespa.athenz.client;

import com.yahoo.vespa.athenz.client.common.ClientBase;
import org.apache.http.client.methods.HttpUriRequest;

public interface ErrorHandler {
    static ErrorHandler empty() {
        return (r, e) -> {
        };
    }

    void reportError(HttpUriRequest request, Exception error);
}
