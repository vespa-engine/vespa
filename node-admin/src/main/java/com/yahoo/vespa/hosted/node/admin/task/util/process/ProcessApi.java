// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import java.nio.file.Path;

/**
 * Process abstraction.
 *
 * @author hakonhall
 */
interface ProcessApi extends AutoCloseable {
    void waitForTermination();
    int exitCode();
    String getUtf8Output();

    @Override
    void close();

    // For testing only
    Path getProcessOutputPath();
}
