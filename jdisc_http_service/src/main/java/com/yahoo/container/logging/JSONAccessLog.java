// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.container.core.AccessLogConfig;

/**
 * Log a message in Vespa JSON access log format.
 *
 * @author frodelu
 * @author Tony Vaagenes
 */
public final class JSONAccessLog implements RequestLogHandler {

    private final AccessLogHandler logHandler;

    public JSONAccessLog(AccessLogConfig config) {
        logHandler = new AccessLogHandler(config.fileHandler(), new JSONFormatter());
    }

    @Override
    public void log(RequestLogEntry entry) {
        logHandler.log(entry);
    }

    // TODO: This is never called. We should have a DI provider and call this method from its deconstruct.
    public void shutdown() {
        logHandler.shutdown();
    }
}
