package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by sgrostad on 12/07/2017.
 */
public class ExecuteCommandTest {
    @Test
    public void test_executeCommand_reading_executeTestFile_with_cat() throws IOException {
        CommandExecutor commandExecutor = new CommandExecutor();
        String command = "cat src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/executeTestFile.txt";
        ArrayList<String> commandOutput = commandExecutor.executeCommand(command);
        List<String> expectedOutput = Arrays.asList("This file test","if executeCommand","reads","this file properly.");
        assertEquals(expectedOutput, commandOutput);
    }
}