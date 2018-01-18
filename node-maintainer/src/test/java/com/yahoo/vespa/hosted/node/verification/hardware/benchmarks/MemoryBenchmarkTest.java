// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.hardware.benchmarks;

import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseResult;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author sgrostad
 * @author olaaun
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
        List<String> mockCommandOutput = commandExecutor.outputFromString(mockOutput);
        Optional<ParseResult> parseResult = memoryBenchmark.parseMemorySpeed(mockCommandOutput);
        ParseResult expectedParseResult = new ParseResult("GB/s", expectedSpeed.toString());
        assertEquals(Optional.of(expectedParseResult), parseResult);
    }

    @Test
    public void parseMemorySpeed_invalid_output() throws Exception {
        List<String> mockCommandOutput = commandExecutor.outputFromString("");
        Optional<ParseResult> parseResult = memoryBenchmark.parseMemorySpeed(mockCommandOutput);
        assertEquals(Optional.empty(), parseResult);
        mockCommandOutput = commandExecutor.outputFromString("Exit status 1");
        parseResult = memoryBenchmark.parseMemorySpeed(mockCommandOutput);
        assertEquals(Optional.empty(), parseResult);
    }
}
