// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

/**
 * @author sgrostad
 * @author olaaun
 */

public class CommandExecutorTest {

    private CommandExecutor commandExecutor;

    @Before
    public void setup() {
        commandExecutor = new CommandExecutor();
    }

    @Test
    public void test_if_executeAString_reads_testReadFile_correct() throws IOException {
        String command = "cat src/test/java/com/yahoo/vespa/hosted/node/verification/hardware/resources/testReadFile.txt";
        List<String> commandOutput = commandExecutor.executeCommand(command);
        List<String> expectedOutput = asList("This test file tests apache commons exec", "Second line");
        assertEquals(expectedOutput, commandOutput);
    }

}
