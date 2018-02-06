// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.jdisc.Timer;
import com.yahoo.log.LogLevel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil.uncheck;

/**
 * @author hakonhall
 */
public class ChildProcess2Impl implements ChildProcess2 {
    private static final Logger logger = Logger.getLogger(ChildProcess2Impl.class.getName());

    private final CommandLine commandLine;
    private final ProcessApi2 process;
    private final Path outputPath;
    private final Timer timer;

    public ChildProcess2Impl(CommandLine commandLine,
                             ProcessApi2 process,
                             Path outputPath,
                             Timer timer) {
        this.commandLine = commandLine;
        this.process = process;
        this.outputPath = outputPath;
        this.timer = timer;
    }

    @Override
    public void waitForTermination() {
        Duration timeoutDuration = commandLine.getTimeout();
        Instant timeout = timer.currentTime().plus(timeoutDuration);
        long maxOutputBytes = commandLine.getMaxOutputBytes();

        // How frequently do we want to wake up and check the output file size?
        final Duration pollInterval = Duration.ofSeconds(10);

        boolean hasTerminated = false;
        while (!hasTerminated) {
            Instant now = timer.currentTime();
            long sleepPeriodMillis = pollInterval.toMillis();
            if (now.plusMillis(sleepPeriodMillis).isAfter(timeout)) {
                sleepPeriodMillis = Duration.between(now, timeout).toMillis();

                if (sleepPeriodMillis <= 0) {
                    gracefullyKill();
                    throw new TimeoutChildProcessException(
                            timeoutDuration, commandLine.toString(), getOutput());
                }
            }

            try {
                hasTerminated = process.waitFor(sleepPeriodMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // Ignore, just loop around.
                continue;
            }

            // Always check output file size to ensure we don't load too much into memory.
            long sizeInBytes = uncheck(() -> Files.size(outputPath));
            if (sizeInBytes > maxOutputBytes) {
                gracefullyKill();
                throw new LargeOutputChildProcessException(
                        sizeInBytes, commandLine.toString(), getOutput());
            }
        }
    }

    @Override
    public int exitCode() {
        return process.exitValue();
    }

    @Override
    public String getOutput() {
        byte[] bytes = uncheck(() -> Files.readAllBytes(outputPath));
        return new String(bytes, commandLine.getOutputEncoding());
    }

    @Override
    public void close() {
        try {
            Files.delete(outputPath);
        } catch (Throwable t) {
            logger.log(LogLevel.WARNING, "Failed to delete " + outputPath, t);
        }
    }

    Path getOutputPath() {
        return outputPath;
    }

    private void gracefullyKill() {
        process.destroy();

        Duration maxWaitAfterSigTerm = commandLine.getSigTermGracePeriod();
        Instant timeout = timer.currentTime().plus(maxWaitAfterSigTerm);
        if (!waitForTermination(timeout)) {
            process.destroyForcibly();

            // If waiting for the process now takes a long time, it's probably a kernel issue
            // or huge core is getting dumped.
            Duration maxWaitAfterSigKill = commandLine.getSigKillGracePeriod();
            if (!waitForTermination(timer.currentTime().plus(maxWaitAfterSigKill))) {
                throw new UnkillableChildProcessException(
                        maxWaitAfterSigTerm,
                        maxWaitAfterSigKill,
                        commandLine.toString(),
                        getOutput());
            }
        }
    }

    /** @return true if process terminated, false on timeout. */
    private boolean waitForTermination(Instant timeout) {
        while (true) {
            long waitDurationMillis = Duration.between(timer.currentTime(), timeout).toMillis();
            if (waitDurationMillis <= 0) {
                return false;
            }

            try {
                return process.waitFor(waitDurationMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }
}
