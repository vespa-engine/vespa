// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.system.execution;

/**
 * @author bjorncs
 * @author gjoranv
 */
public class ProcessResult {
    public final String stdOut;
    public final String stdErr;
    public final int exitCode;

    public ProcessResult(int exitCode, String stdOut, String stdErr) {
        this.exitCode = exitCode;
        this.stdOut = stdOut;
        this.stdErr = stdErr;
    }

}
