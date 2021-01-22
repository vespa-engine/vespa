// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author mortent
 */
public class FileConnectionLog extends AbstractComponent implements ConnectionLog, LogWriter<ConnectionLogEntry> {

    private static final Logger logger = Logger.getLogger(FileConnectionLog.class.getName());
    private final ConnectionLogHandler logHandler;

    @Inject
    public FileConnectionLog(ConnectionLogConfig config) {
        logHandler = new ConnectionLogHandler(config.cluster(), this);
    }

    @Override
    public void log(ConnectionLogEntry connectionLogEntry) {
        logHandler.log(connectionLogEntry);
    }

    @Override
    public void deconstruct() {
        logHandler.shutdown();
    }

    @Override
    // TODO serialize directly to outputstream
    public void write(ConnectionLogEntry entry, OutputStream outputStream) throws IOException {
        outputStream.write(entry.toJson().getBytes(StandardCharsets.UTF_8));
    }
}