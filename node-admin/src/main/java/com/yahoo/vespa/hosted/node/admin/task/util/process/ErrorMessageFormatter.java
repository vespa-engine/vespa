// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

/**
 * @author hakonhall
 */
class ErrorMessageFormatter {
    static final int MAX_OUTPUT_PREFIX = 200;
    static final int MAX_OUTPUT_SUFFIX = 200;
    // Omitting a number of chars less than 10 or less than 10% would be ridiculous.
    static final int MAX_OUTPUT_SLACK = Math.max(10, (10 * (MAX_OUTPUT_PREFIX + MAX_OUTPUT_SUFFIX)) / 100);

    /**
     * Creates an error message suitable for an exception or logging, that includes the
     * command line and output of a terminated process. The output is truncated if too large.
     * Format on resulting message:
     *   Command 'COMMANDLINE' PROBLEM: stdout/stderr: 'OUTPUT'
     *
     * @param problem           E.g. "terminated with exit code 1"
     * @param childProcess      A terminated child process
     */
    static String createSnippetForTerminatedProcess(String problem, ChildProcess childProcess) {
        return createSnippetForTerminatedProcessWith(
                problem, childProcess, MAX_OUTPUT_PREFIX, MAX_OUTPUT_SUFFIX, MAX_OUTPUT_SLACK);
    }

    static String createSnippetForTerminatedProcessWith(String problem,
                                                        ChildProcess childProcess,
                                                        int maxOutputPrefix,
                                                        int maxOutputSuffix,
                                                        int maxOutputSlack) {
        StringBuilder stringBuilder = new StringBuilder()
                .append("Command '")
                .append(childProcess.commandLine())
                .append("' ")
                .append(problem)
                .append(": stdout/stderr: '");

        String possiblyHugeOutput = childProcess.getUtf8Output();
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
