// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

import java.util.concurrent.TimeUnit;

/**
 * Process abstraction.
 *
 * @author hakonhall
 */
public interface ProcessApi2 {
    boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException;
    int exitValue();
    void destroy();
    void destroyForcibly();
}
