package com.yahoo.vespa.hosted.node.verification.hardware;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

/**
 * Created by sgrostad on 12/07/2017.
 */
public class CommandExecutorTest {

    private CommandExecutor commandExecutor;


    @Before
    public void setup(){
        commandExecutor = new CommandExecutor();
    }
    @Test
    public void test_if_executeAString_reads_testReadFile_correct() throws IOException{
        String command = "cat src/test/java/com/yahoo/vespa/hosted/node/verification/hardware/resources/testReadFile.txt";
        ArrayList<String> commandOutput = commandExecutor.executeCommand(command);
        List<String> expectedOutput = asList("This test file tests apache commons exec", "Second line");
        assertEquals(expectedOutput,commandOutput);
    }
}