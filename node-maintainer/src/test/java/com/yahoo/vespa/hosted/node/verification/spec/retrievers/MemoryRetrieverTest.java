// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseResult;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author sgrostad
 * @author olaaun
 */

public class MemoryRetrieverTest {

    private static final String FILENAME = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/meminfoTest";
    private HardwareInfo hardwareInfo;
    private MockCommandExecutor commandExecutor;
    private MemoryRetriever memory;
    private final double DELTA = 0.1;

    @Before
    public void setup() {
        hardwareInfo = new HardwareInfo();
        commandExecutor = new MockCommandExecutor();
        memory = new MemoryRetriever(hardwareInfo, commandExecutor);
    }

    @Test
    public void updateInfo_should_set_memory_available_in_hardwareInfo() {
        commandExecutor.addCommand("cat " + FILENAME);
        memory.updateInfo();
        double expectedMemory = 4.042128;
        assertEquals(expectedMemory, hardwareInfo.getMinMainMemoryAvailableGb(), DELTA);
    }

    @Test
    public void parseMemInfoFile_should_return_valid_parseResult() throws IOException {
        List<String> commandOutput = MockCommandExecutor.readFromFile(FILENAME);
        ParseResult parseResult = memory.parseMemInfoFile(commandOutput);
        ParseResult expectedParseResult = new ParseResult("MemTotal", "4042128 kB");
        assertEquals(expectedParseResult, parseResult);
    }

    @Test
    public void updateMemoryInfo_valid_input() {
        ParseResult testParseResult = new ParseResult("MemTotal", "4042128");
        memory.updateMemoryInfo(testParseResult);
        double expectedMemory = 4.042128;
        assertEquals(expectedMemory, hardwareInfo.getMinMainMemoryAvailableGb(), DELTA);
    }

    @Test
    public void convertToGB_valid_input() {
        String testTotMem = "4042128";
        double expectedTotMem = 4.042128;
        assertEquals(expectedTotMem, memory.convertKBToGB(testTotMem), DELTA);
    }

}
