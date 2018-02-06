// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.log.LogLevel;

import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil.uncheck;

/**
 * @author hakonhall
 */
public class ProcessStarterImpl implements ProcessStarter {
    private static final Logger logger = Logger.getLogger(ProcessStarterImpl.class.getName());

    @Override
    public ProcessApi2 start(ProcessBuilder processBuilder) {
        if (logger.isLoggable(LogLevel.DEBUG)) {
            logger.log(LogLevel.DEBUG, "Spawning process: " + processBuilder.command());
        }

        Process process = uncheck(() -> processBuilder.start());
        return new ProcessApi2Impl(process);
    }
}
