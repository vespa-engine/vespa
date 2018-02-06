// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil.uncheck;

/**
 * @author hakonhall
 */
public class ProcessStarterImpl implements ProcessStarter {
    @Override
    public ProcessApi2 start(ProcessBuilder processBuilder) {
        Process process = uncheck(() -> processBuilder.start());
        return new ProcessApi2Impl(process);
    }
}
