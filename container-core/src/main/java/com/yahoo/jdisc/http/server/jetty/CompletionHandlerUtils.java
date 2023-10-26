// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.handler.CompletionHandler;

/**
 * @author bjorncs
 */
public interface CompletionHandlerUtils {
    CompletionHandler NOOP_COMPLETION_HANDLER = new CompletionHandler() {
        @Override public void completed() {}
        @Override public void failed(final Throwable t) {}
    };
}
