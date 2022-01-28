// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.text.internal.SnippetGenerator;

/**
 * Base class for child process related exceptions, with a util to build an error message
 * that includes a large part of the output.
 *
 * @author hakonhall
 */
@SuppressWarnings("serial")
public abstract class ChildProcessException extends RuntimeException {
    private static final SnippetGenerator snippetGenerator = new SnippetGenerator();

    /**
     * An exception with a message of the following format:
     *   Command 'COMMANDLINE' PROBLEM: stdout/stderr: 'OUTPUT'
     *
     * If the output of the terminated command is too large it will be sampled.
     *
     * @param problem            E.g. "terminated with exit code 1"
     * @param commandLine        The command that failed in a concise (e.g. shell-like) format
     * @param possiblyHugeOutput The output of the command
     */
    protected ChildProcessException(String problem, String commandLine, String possiblyHugeOutput) {
        super(makeSnippet(problem, commandLine, possiblyHugeOutput));
    }

    protected ChildProcessException(RuntimeException cause,
                                    String problem,
                                    String commandLine,
                                    String possiblyHugeOutput) {
        super(makeSnippet(problem, commandLine, possiblyHugeOutput), cause);
    }

    private static String makeSnippet(String problem, String commandLine, String possiblyHugeOutput) {
        return "Command '" +
                commandLine +
                "' " +
                problem +
                ": stdout/stderr: '" +
                snippetGenerator.makeSnippet(possiblyHugeOutput, 500) +
                "'";
    }
}
