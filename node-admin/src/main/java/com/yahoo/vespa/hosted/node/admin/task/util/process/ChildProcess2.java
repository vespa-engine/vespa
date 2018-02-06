// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

/**
 * @author hakonhall
 */
interface ChildProcess2 extends AutoCloseable {
    void waitForTermination();
    int exitCode();
    String getOutput();

    /** Close/cleanup any resources held. Must not throw an exception. */
    @Override
    void close();
}
