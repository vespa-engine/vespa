// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ErrorMessageFormatterTest {
    private String createFullSnippet(String problem, String commandLine, String output) {
        return createSnippet(problem, commandLine, output, 1000, 1000, 1000);
    }

    private String createSnippet(String problem,
                                 String commandLine,
                                 String output,
                                 int maxOutputPrefix,
                                 int maxOutputSuffix,
                                 int maxOutputSlack) {
        ChildProcess childProcess = mock(ChildProcess.class);
        when(childProcess.commandLine()).thenReturn(commandLine);
        when(childProcess.getUtf8Output()).thenReturn(output);
        return ErrorMessageFormatter.createSnippetForTerminatedProcessWith(
                problem, childProcess, maxOutputPrefix, maxOutputSuffix, maxOutputSlack);
    }

    @Test
    public void verifyWithoutTrimming() {
        assertEquals("Command 'command line' problem: stdout/stderr: 'output'",
                createFullSnippet("problem", "command line", "output"));
    }

    @Test
    public void verifyWithTrimming() {
        assertEquals(
                "Command 'command line' problem: stdout/stderr: 'Thi... [15 chars omitted] ...sage'",
                createSnippet(
                        "problem",
                        "command line",
                        "This is a long message",
                        3,
                        4,
                        14));
    }

    @Test
    public void verifySlack() {
        assertEquals(
                "Command 'command line' problem: stdout/stderr: 'This is a long message'",
                createSnippet(
                        "problem",
                        "command line",
                        "This is a long message",
                        3,
                        4,
                        16));
    }
}