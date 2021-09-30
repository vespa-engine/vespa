// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.vespa.hosted.node.admin.container.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author bjorncs
 */
abstract class AbstractProducer implements ArtifactProducer {

    private final Logger log = Logger.getLogger(getClass().getName());

    private final ContainerOperations container;

    protected AbstractProducer(ContainerOperations container) { this.container = container; }

    protected ContainerOperations container() { return container; }

    protected CommandResult executeCommand(NodeAgentContext ctx, List<String> command, boolean logOutput) throws IOException {
        CommandResult result = container.executeCommandInContainerAsRoot(ctx, command.toArray(new String[0]));
        String cmdString = command.stream().map(s -> "'" + s + "'").collect(Collectors.joining(" ", "\"", "\""));
        int exitCode = result.getExitCode();
        String output = result.getOutput().trim();
        String prefixedOutput = output.contains("\n")
                ? "\n" + output
                : (output.isEmpty() ? "<no output>" : output);
        if (exitCode > 0) {
            String errorMsg = logOutput
                    ? String.format("Failed to execute %s (exited with code %d): %s", cmdString, exitCode, prefixedOutput)
                    : String.format("Failed to execute %s (exited with code %d)", cmdString, exitCode);
            throw new IOException(errorMsg);
        } else {
            String logMsg = logOutput
                    ? String.format("Executed command %s. Exited with code %d and output: %s", cmdString, exitCode, prefixedOutput)
                    : String.format("Executed command %s. Exited with code %d.", cmdString, exitCode);
            ctx.log(log, logMsg);
        }
        return result;
    }

    protected int findVespaServicePid(NodeAgentContext ctx, String configId) throws IOException {
        Path findPidBinary = ctx.pathInNodeUnderVespaHome("libexec/vespa/find-pid");
        CommandResult findPidResult = executeCommand(ctx, List.of(findPidBinary.toString(), configId), true);
        return Integer.parseInt(findPidResult.getOutput());
    }

    protected double duration(NodeAgentContext ctx, ServiceDumpReport.DumpOptions options, double defaultValue) {
        double duration = options != null && options.duration() != null && options.duration() > 0
                ? options.duration() : defaultValue;
        double maxDuration = 300;
        if (duration > maxDuration) {
            ctx.log(log, Level.WARNING,
                    String.format("Specified duration %.3fs longer than max allowed (%.3fs)", duration, maxDuration));
            return maxDuration;
        }
        return duration;
    }

}
