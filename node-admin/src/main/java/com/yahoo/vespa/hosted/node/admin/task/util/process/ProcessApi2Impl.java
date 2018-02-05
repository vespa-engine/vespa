// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

import java.util.concurrent.TimeUnit;

/**
 * @author hakonhall
 */
public class ProcessApi2Impl implements ProcessApi2 {
    private final Process process;

    ProcessApi2Impl(Process process) {
        this.process = process;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        return process.waitFor(timeout, unit);
    }

    @Override
    public int exitValue() {
        return process.exitValue();
    }

    @Override
    public void destroy() {
        process.destroy();
    }

    @Override
    public void destroyForcibly() {
        process.destroyForcibly();
    }
}
