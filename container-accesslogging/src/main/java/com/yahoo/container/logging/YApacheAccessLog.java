// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.container.core.AccessLogConfig;

import java.util.logging.Level;

/**
 * Log a message in yApache log format.
 *
 * @author tinyv
 */
public final class YApacheAccessLog implements  AccessLogInterface {

    private final AccessLogHandler logHandler;

    public YApacheAccessLog(AccessLogConfig config) {
        logHandler = new AccessLogHandler(config.fileHandler());
    }

    @Override
    public void log(final AccessLogEntry logEntry) {
        logHandler.access.log(Level.INFO, new YApacheFormatter(logEntry).format() + '\n');
    }


    // TODO: This is never called. We should have a DI provider and call this method from its deconstruct.
    public void shutdown() {
        logHandler.shutdown();
    }

    void rotateNow() {
        logHandler.rotateNow();
    }

}
