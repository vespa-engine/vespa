// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import java.util.Optional;

/**
 * @author hakonhall
 */
public class TestChildProcess2 implements ChildProcess2 {
    private final int exitCode;
    private final String output;
    private Optional<RuntimeException> exceptionToThrowInWaitForTermination = Optional.empty();
    private boolean closeCalled = false;

    public TestChildProcess2(int exitCode, String output) {
        this.exitCode = exitCode;
        this.output = output;
    }

    public void throwInWaitForTermination(RuntimeException e) {
        this.exceptionToThrowInWaitForTermination = Optional.of(e);
    }

    @Override
    public void waitForTermination() {
        if (exceptionToThrowInWaitForTermination.isPresent()) {
            throw exceptionToThrowInWaitForTermination.get();
        }
    }

    @Override
    public int exitCode() {
        return exitCode;
    }

    @Override
    public String getOutput() {
        return output;
    }

    @Override
    public void close() {
        if (closeCalled) {
            throw new IllegalStateException("close already called");
        }
        closeCalled = true;
    }

    public boolean closeCalled() {
        return closeCalled;
    }
}
