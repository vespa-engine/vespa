// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import java.util.Objects;

// TODO: Consider replacing usages of this with CommandResult when docker-api module can be removed
public class ProcessResult {
    private final int exitStatus;
    private final String output;
    private final String errors;

    public ProcessResult(int exitStatus, String output, String errors) {
        this.exitStatus = exitStatus;
        this.output = output;
        this.errors = errors;
    }

    public boolean isSuccess() { return exitStatus == 0; }
    public int getExitStatus() { return exitStatus; }

    public String getOutput() { return output; }

    public String getErrors() { return errors; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProcessResult)) return false;
        ProcessResult other = (ProcessResult) o;
        return Objects.equals(exitStatus, other.exitStatus)
                && Objects.equals(output, other.output)
                && Objects.equals(errors, other.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exitStatus, output, errors);
    }

    @Override
    public String toString() {
        return "ProcessResult {"
                + " exitStatus=" + exitStatus
                + " output=" + output
                + " errors=" + errors
                + " }";
    }
}
