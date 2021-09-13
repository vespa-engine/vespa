// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.vespa.hosted.node.admin.container.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandResult;

import java.io.IOException;
import java.util.List;
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
        String logMsg = logOutput
                ? String.format("Executed command %s.\nExited with code %d and output: %s", cmdString, exitCode, prefixedOutput)
                : String.format("Executed command %s.\nExited with code %d.", cmdString, exitCode);
        ctx.log(log, logMsg);
        if (exitCode > 0) {
            String errorMsg = logOutput
                    ? String.format("Failed to execute %s: %s", cmdString, prefixedOutput)
                    : String.format("Failed to execute %s (exited with code %d)", cmdString, exitCode);
            throw new IOException(errorMsg);
        }
        return result;
    }


}
