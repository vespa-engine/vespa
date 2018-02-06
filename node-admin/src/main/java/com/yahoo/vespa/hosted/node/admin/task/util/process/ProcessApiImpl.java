// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.nio.file.Path;

/**
 * @author hakonhall
 */
public class ProcessApiImpl implements ProcessApi {
    private final Process process;
    private final Path processOutputPath;

    ProcessApiImpl(Process process, Path processOutputPath) {
        this.process = process;
        this.processOutputPath = processOutputPath;
    }

    @Override
    public void waitForTermination() {
        while (true) {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                // ignoring
                continue;
            }

            return;
        }
    }

    @Override
    public int exitCode() { return process.exitValue(); }

    @Override
    public String getUtf8Output() {
        return new UnixPath(processOutputPath).readUtf8File();
    }

    @Override
    public void close() {
        processOutputPath.toFile().delete();
    }

    @Override
    public Path getProcessOutputPath() {
        return processOutputPath;
    }
}
