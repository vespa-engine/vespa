package com.yahoo.vespa.hosted.node.verification.hardware.benchmarks;

import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseResult;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by olaa on 14/07/2017.
 */
public class MemoryBenchmarkTest {

    private MemoryBenchmark memoryBenchmark;
    private BenchmarkResults benchmarkResults;
    private MockCommandExecutor commandExecutor;
    private static final double DELTA = 0.1;

    @Before
    public void setup() {
        benchmarkResults = new BenchmarkResults();
        commandExecutor = new MockCommandExecutor();
        memoryBenchmark = new MemoryBenchmark(benchmarkResults, commandExecutor);
    }

    @Test
    public void doBenchMark_should_update_read_and_write_memory_speed() {
        commandExecutor.addDummyCommand();
        commandExecutor.addDummyCommand();
        commandExecutor.addCommand("cat src/test/java/com/yahoo/vespa/hosted/node/verification/hardware/resources/validMemoryWriteSpeed");
        commandExecutor.addCommand("cat src/test/java/com/yahoo/vespa/hosted/node/verification/hardware/resources/validMemoryReadSpeed");
        commandExecutor.addDummyCommand();
        commandExecutor.addDummyCommand();
        memoryBenchmark.doBenchmark();
        double expectedReadSpeed = 5.9;
        double expectedWriteSpeed = 3.4;
        assertEquals(expectedReadSpeed, benchmarkResults.getMemoryReadSpeedGBs(), DELTA);
        assertEquals(expectedWriteSpeed, benchmarkResults.getMemoryWriteSpeedGBs(), DELTA);
    }

    @Test
    public void parseMemorySpeed_valid_output() throws Exception {
        Double expectedSpeed = 12.1;
        String mockOutput = "This is a test \n the memory speed to be found is " + expectedSpeed + " GB/s";
        ArrayList<String> mockCommandOutput = commandExecutor.outputFromString(mockOutput);
        ParseResult parseResult = memoryBenchmark.parseMemorySpeed(mockCommandOutput);
        ParseResult expectedParseResult = new ParseResult("GB/s", expectedSpeed.toString());
        assertEquals(expectedParseResult, parseResult);
    }

    @Test
    public void parseMemorySpeed_invalid_output() throws Exception {
        ArrayList<String> mockCommandOutput = commandExecutor.outputFromString("");
        ParseResult parseResult = memoryBenchmark.parseMemorySpeed(mockCommandOutput);
        ParseResult expectedParseResult = new ParseResult("invalid", "invalid");
        assertEquals(expectedParseResult, parseResult);
        mockCommandOutput = commandExecutor.outputFromString("Exit status 1");
        parseResult = memoryBenchmark.parseMemorySpeed(mockCommandOutput);
        assertEquals(expectedParseResult, parseResult);
    }

    @Test
    public void memoryReadSpeed_valid_input_should_update_hardwareResults() {
        Double expectedMemoryReadSpeed = 12.1;
        memoryBenchmark.updateMemoryReadSpeed(expectedMemoryReadSpeed.toString());
        assertEquals(expectedMemoryReadSpeed, benchmarkResults.getMemoryReadSpeedGBs(), DELTA);
    }

    @Test
    public void memoryReadSpeed_invalid_input_should_not_update_hardwareResults() {
        memoryBenchmark.updateMemoryReadSpeed("Invalid speed");
        assertEquals(0D, benchmarkResults.getMemoryReadSpeedGBs(), DELTA);
    }

    @Test
    public void memoryWriteSpeed_valid_input_should_update_hardwareResults() {
        Double expectedMemoryWriteSpeed = 3.8;
        memoryBenchmark.updateMemoryWriteSpeed(expectedMemoryWriteSpeed.toString());
        assertEquals(expectedMemoryWriteSpeed, benchmarkResults.getMemoryWriteSpeedGBs(), DELTA);
    }

    @Test
    public void memoryWriteSpeed_invalid_input_should_not_update_hardwareResults() {
        memoryBenchmark.updateMemoryWriteSpeed("Invalid speed");
        assertEquals(0D, benchmarkResults.getMemoryWriteSpeedGBs(), DELTA);
    }

    @Test
    public void isValidMemory_should_return_true_when_parameter_is_number() {
        String benchmarkOutput = "6.32";
        boolean validMemory = memoryBenchmark.isValidMemory(benchmarkOutput);
        assertTrue(validMemory);
    }

    @Test
    public void isValidMemory_should_return_false_when_parameter_is_not_number() {
        String benchmarkOutput = "";
        boolean validMemory = memoryBenchmark.isValidMemory(benchmarkOutput);
        assertFalse(validMemory);
        benchmarkOutput = null;
        validMemory = memoryBenchmark.isValidMemory(benchmarkOutput);
        assertFalse(validMemory);
        benchmarkOutput = "Exit status 127";
        validMemory = memoryBenchmark.isValidMemory(benchmarkOutput);
        assertFalse(validMemory);
    }

}