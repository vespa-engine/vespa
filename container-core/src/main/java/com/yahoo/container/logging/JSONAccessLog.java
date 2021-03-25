// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.component.AbstractComponent;
import com.yahoo.container.core.AccessLogConfig;

/**
 * Log a message in Vespa JSON access log format.
 *
 * @author frodelu
 * @author Tony Vaagenes
 */
public final class JSONAccessLog extends AbstractComponent implements RequestLogHandler {

    private final AccessLogHandler logHandler;

    public JSONAccessLog(AccessLogConfig config) {
        logHandler = new AccessLogHandler(config.fileHandler(), new JSONFormatter());
    }

    @Override
    public void log(RequestLogEntry entry) {
        logHandler.log(entry);
    }

    @Override public void deconstruct() { logHandler.shutdown(); }
}
