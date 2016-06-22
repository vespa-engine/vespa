// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import java.util.Objects;

public class ProcessResult {
    private final int exitStatus;
    private final String output;

    public ProcessResult(int exitStatus, String output) {
        this.exitStatus = exitStatus;
        this.output = output;
    }

    public boolean isSuccess() { return exitStatus == 0; }
    public int getExitStatus() { return exitStatus; }

    /**
     * @return The combined stdout and stderr output from the process.
     */
    public String getOutput() { return output; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProcessResult)) return false;
        ProcessResult other = (ProcessResult) o;
        return Objects.equals(exitStatus, other.exitStatus)
                && Objects.equals(output, other.output);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exitStatus, output);
    }

    @Override
    public String toString() {
        return "ProcessResult {"
                + " exitStatus=" + exitStatus
                + " output=" + output
                + " }";
    }
}
