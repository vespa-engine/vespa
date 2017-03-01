// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.system.execution;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author gjoranv
 * @author bjorncs
 */
public class ProcessExecutor {

    public static class Builder {
        private final int timeoutSeconds;
        private int[] successExitCodes;

        public Builder(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public Builder setSuccessExitCodes(int... successExitCodes) {
            this.successExitCodes = successExitCodes;
            return this;
        }

        public ProcessExecutor build() {
            return new ProcessExecutor(timeoutSeconds, successExitCodes);
        }
    }

    private ProcessExecutor(int timeoutSeconds, int[] successExitCodes) {
        this.timeoutSeconds = timeoutSeconds;
        this.successExitCodes = successExitCodes;
    }

    public final int timeoutSeconds;
    private final int[] successExitCodes;

    public Optional<ProcessResult> execute(String command) throws IOException {
        return execute(command, null);
    }

    public Optional<ProcessResult> execute(String command, String processInput) throws IOException {
        ByteArrayOutputStream processErr = new ByteArrayOutputStream();
        ByteArrayOutputStream processOut = new ByteArrayOutputStream();

        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(createStreamHandler(processOut, processErr, processInput));
        ExecuteWatchdog watchDog = new ExecuteWatchdog(TimeUnit.SECONDS.toMillis(timeoutSeconds));
        executor.setWatchdog(watchDog);
        executor.setExitValues(successExitCodes);

        int exitCode = executor.execute(CommandLine.parse(command));
        return (watchDog.killedProcess()) ?
                Optional.empty() : Optional.of(new ProcessResult(exitCode, processOut.toString(), processErr.toString()));
    }

    private static PumpStreamHandler createStreamHandler(ByteArrayOutputStream processOut,
                                                         ByteArrayOutputStream processErr,
                                                         String input) {
        if (input != null) {
            InputStream processInput = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
            return new PumpStreamHandler(processOut, processErr, processInput);
        } else {
            return new PumpStreamHandler(processOut, processErr);
        }
    }

}
