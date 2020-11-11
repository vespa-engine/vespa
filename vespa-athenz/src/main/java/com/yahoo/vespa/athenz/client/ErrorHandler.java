package com.yahoo.vespa.athenz.client;

public interface ErrorHandler {
    static ErrorHandler empty() {
        return (r, e) -> {
        };
    }

    void reportError(RequestProperties request, Exception error);

    interface RequestProperties {
        String hostname();
    }
}
