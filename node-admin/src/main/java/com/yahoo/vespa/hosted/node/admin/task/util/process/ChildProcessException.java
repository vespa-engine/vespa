// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

/**
 * Base class for child process related exceptions, with a util to build an error message
 * that includes a large part of the output.
 *
 * @author hakonhall
 */
@SuppressWarnings("serial")
public abstract class ChildProcessException extends RuntimeException {
    private static final int MAX_OUTPUT_PREFIX = 200;
    private static final int MAX_OUTPUT_SUFFIX = 200;
    // Omitting a number of chars less than 10 or less than 10% would be ridiculous.
    private static final int MAX_OUTPUT_SLACK = Math.max(10, (10 * (MAX_OUTPUT_PREFIX + MAX_OUTPUT_SUFFIX)) / 100);

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

    private static String makeSnippet(String problem,
                               String commandLine,
                               String possiblyHugeOutput) {
        return makeSnippet(
                problem,
                commandLine,
                possiblyHugeOutput,
                MAX_OUTPUT_PREFIX,
                MAX_OUTPUT_SUFFIX,
                MAX_OUTPUT_SLACK);
    }

    // Package-private instead of private for testing.
    static String makeSnippet(String problem,
                              String commandLine,
                              String possiblyHugeOutput,
                              int maxOutputPrefix,
                              int maxOutputSuffix,
                              int maxOutputSlack) {
        StringBuilder stringBuilder = new StringBuilder()
                .append("Command '")
                .append(commandLine)
                .append("' ")
                .append(problem)
                .append(": stdout/stderr: '");

        if (possiblyHugeOutput.length() <= maxOutputPrefix + maxOutputSuffix + maxOutputSlack) {
            stringBuilder.append(possiblyHugeOutput);
        } else {
            stringBuilder.append(possiblyHugeOutput.substring(0, maxOutputPrefix))
                    .append("... [")
                    .append(possiblyHugeOutput.length() - maxOutputPrefix - maxOutputSuffix)
                    .append(" chars omitted] ...")
                    .append(possiblyHugeOutput.substring(possiblyHugeOutput.length() - maxOutputSuffix));
        }

        stringBuilder.append("'");

        return stringBuilder.toString();
    }
}
