// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
