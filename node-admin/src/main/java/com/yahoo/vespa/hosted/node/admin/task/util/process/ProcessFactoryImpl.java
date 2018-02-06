// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.jdisc.Timer;
import com.yahoo.log.LogLevel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil.uncheck;

/**
 * @author hakonhall
 */
public class ProcessFactoryImpl implements ProcessFactory {
    private static final Logger logger = Logger.getLogger(ProcessFactoryImpl.class.getName());
    private static final File DEV_NULL = new File("/dev/null");

    private final ProcessStarter processStarter;
    private final Timer timer;

    ProcessFactoryImpl(ProcessStarter processStarter, Timer timer) {
        this.processStarter = processStarter;
        this.timer = timer;
    }

    @Override
    public ChildProcess2Impl spawn(CommandLine commandLine) {
        List<String> arguments = commandLine.getArguments();
        if (arguments.isEmpty()) {
            throw new IllegalArgumentException("No arguments specified - missing program to spawn");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(arguments);

        if (commandLine.getRedirectStderrToStdoutInsteadOfDiscard()) {
            processBuilder.redirectErrorStream(true);
        } else {
            processBuilder.redirectError(ProcessBuilder.Redirect.to(DEV_NULL));
        }

        // The output is redirected to a temporary file because:
        //  - We could read continuously from process.getInputStream, but that may block
        //    indefinitely with a faulty program.
        //  - If we don't read continuously from process.getInputStream, then because
        //    the underlying channel may be a pipe, the child may be stopped because the pipe
        //    is full.
        //  - To honor the timeout, no API can be used that may end up blocking indefinitely.
        //
        // Therefore, we redirect the output to a file and use waitFor w/timeout. This also
        // has the benefit of allowing for inspection of the file during execution, and
        // allowing the inspection of the file if it e.g. gets too large to hold in-memory.

        String temporaryFilePrefix =
                ProcessFactoryImpl.class.getSimpleName() + "-" + commandLine.programName() + "-";

        FileAttribute<Set<PosixFilePermission>> fileAttribute = PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rw-------"));

        Path temporaryFile = uncheck(() -> Files.createTempFile(
                temporaryFilePrefix,
                ".out",
                fileAttribute));

        try {
            processBuilder.redirectOutput(temporaryFile.toFile());
            ProcessApi2 process = processStarter.start(processBuilder);
            return new ChildProcess2Impl(commandLine, process, temporaryFile, timer);
        } catch (RuntimeException | Error throwable) {
            try {
                Files.delete(temporaryFile);
            } catch (IOException ioException) {
                logger.log(LogLevel.WARNING, "Failed to delete temporary file at " +
                        temporaryFile, ioException);
            }
            throw throwable;
        }

    }
}
