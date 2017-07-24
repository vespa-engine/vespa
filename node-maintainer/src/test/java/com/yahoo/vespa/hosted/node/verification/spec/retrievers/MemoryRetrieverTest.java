package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.spec.mock.MockCommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.parse.ParseResult;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * Created by sgrostad on 06/07/2017.
 */
public class MemoryRetrieverTest {
    private final String FILENAME = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/meminfoTest";

    private HardwareInfo hardwareInfo;
    private MockCommandExecutor commandExecutor;
    private MemoryRetriever memory;
    private final double delta = 0.01;

    @Before
    public void setup(){
        hardwareInfo = new HardwareInfo();
        commandExecutor = new MockCommandExecutor();
        memory = new MemoryRetriever(hardwareInfo, commandExecutor);
    }

    @Test
    public void test_updateInfo_should_set_memory_available_in_hardwareInfo() throws IOException{
        commandExecutor.addCommand("cat " + FILENAME);
        memory.updateInfo();
        double expectedMemory = 4.042128;
        assertEquals(expectedMemory, hardwareInfo.getMinMainMemoryAvailableGb(),delta);
    }

    @Test
    public void test_parseMemInfoFile_should_return_valid_parseResult() throws IOException{
        ArrayList<String> commandOutput = MockCommandExecutor.readFromFile(FILENAME);
        ParseResult parseResult = memory.parseMemInfoFile(commandOutput);
        ParseResult expectedParseResult = new ParseResult("MemTotal", "4042128 kB");
        assertEquals(expectedParseResult, parseResult);
    }

    @Test
    public void test_updateMemoryInfo_valid_input(){
        ParseResult testParseResult = new ParseResult("MemTotal", "4042128");
        memory.updateMemoryInfo(testParseResult);
        double expectedMemory = 4.042128;
        assertEquals(expectedMemory, hardwareInfo.getMinMainMemoryAvailableGb(), delta);
    }

    @Test
    public void test_convertToGB_valid_input(){
        String testTotMem = "4042128";
        double expectedTotMem = 4.042128;
        assertEquals(expectedTotMem, memory.convertKBToGB(testTotMem), delta);
    }
}