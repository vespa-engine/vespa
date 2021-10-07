// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

import java.util.logging.Level;

import java.util.logging.Logger;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author hakonhall
 */
public class ProcessStarterImpl implements ProcessStarter {
    private static final Logger logger = Logger.getLogger(ProcessStarterImpl.class.getName());

    @Override
    public ProcessApi2 start(ProcessBuilder processBuilder) {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Spawning process: " + processBuilder.command());
        }

        Process process = uncheck(processBuilder::start);
        return new ProcessApi2Impl(process);
    }
}
