// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.hardware.benchmarks;


import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseResult;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author sgrostad
 * @author olaaun
 */

public class CPUBenchmarkTest {

    private static final String cpuEuropeanDelimiters = "src/test/java/com/yahoo/vespa/hosted/node/verification/hardware/resources/cpuCyclesWithDotsTimeWithCommaTest.txt";
    private static final String cpuAlternativeDelimiters = "src/test/java/com/yahoo/vespa/hosted/node/verification/hardware/resources/cpuCyclesWithCommasTimeWithDotTest.txt";
    private static final String cpuWrongOutput = "src/test/java/com/yahoo/vespa/hosted/node/verification/hardware/resources/cpuWrongOutputTest.txt";
    private BenchmarkResults benchmarkResults;
    private MockCommandExecutor commandExecutor;
    private CPUBenchmark cpu;
    private static final double DELTA = 0.1;

    @Before
    public void setup() {
        commandExecutor = new MockCommandExecutor();
        benchmarkResults = new BenchmarkResults();
        cpu = new CPUBenchmark(benchmarkResults, commandExecutor);
    }

    @Test
    public void doBenchmark_find_correct_cpuCyclesPerSec() {
        String mockCommand = "cat " + cpuAlternativeDelimiters;
        commandExecutor.addCommand(mockCommand);
        cpu.doBenchmark();
        double result = benchmarkResults.getCpuCyclesPerSec();
        double expected = 2.1576482291815062;
        assertEquals(expected, result, DELTA);
    }

    @Test
    public void doBenchmark_wrong_output_stores_frequency_of_zero() {
        String mockCommand = "cat " + cpuWrongOutput;
        commandExecutor.addCommand(mockCommand);
        cpu.doBenchmark();
        double result = benchmarkResults.getCpuCyclesPerSec();
        double expected = 0;
        assertEquals(expected, result, DELTA);
    }

    @Test
    public void parseCpuCyclesPerSec_return_correct_ArrayList() throws IOException {
        List<String> mockCommandOutput = MockCommandExecutor.readFromFile(cpuEuropeanDelimiters);
        List<ParseResult> parseResults = cpu.parseCpuCyclesPerSec(mockCommandOutput);
        ParseResult expectedParseCyclesResult = new ParseResult("cycles", "2.066.201.729");
        ParseResult expectedParseSecondsResult = new ParseResult("seconds", "0,957617512");
        assertEquals(expectedParseCyclesResult, parseResults.get(0));
        assertEquals(expectedParseSecondsResult, parseResults.get(1));
    }

    @Test
    public void test_if_setCpuCyclesPerSec_reads_output_correctly() {
        List<ParseResult> parseResults = new ArrayList<>();
        parseResults.add(new ParseResult("cycles", "2.066.201.729"));
        parseResults.add(new ParseResult("seconds", "0,957617512"));
        cpu.setCpuCyclesPerSec(parseResults);
        double expectedCpuCyclesPerSec = 2.1576482291815062;
        assertEquals(expectedCpuCyclesPerSec, benchmarkResults.getCpuCyclesPerSec(), DELTA);
    }

    @Test
    public void test_if_makeCyclesDouble_converts_European_and_alternative_delimiters_correctly() {
        String toBeConvertedEuropean = "2.066.201.729";
        String toBEConvertedAlternative = "2,066,201,729";
        double expectedCycles = 2066201729;
        assertEquals(expectedCycles, cpu.makeCyclesDouble(toBeConvertedEuropean), DELTA);
        assertEquals(expectedCycles, cpu.makeCyclesDouble(toBEConvertedAlternative), DELTA);
    }

    @Test
    public void test_if_makeSecondsDouble_converts_European_and_alternative_delimiters_correctly() {
        String toBeConvertedEuropean = "0,957617512";
        String toBEConvertedAlternative = "0.957617512";
        double expectedSeconds = 0.957617512;
        assertEquals(expectedSeconds, cpu.makeSecondsDouble(toBeConvertedEuropean), DELTA);
        assertEquals(expectedSeconds, cpu.makeSecondsDouble(toBEConvertedAlternative), DELTA);
    }

    @Test
    public void test_if_checkIfNumber_returns_true() {
        String number = "125.5";
        assertTrue(cpu.checkIfNumber(number));
    }

    @Test
    public void test_if_checkIfNumber_returns_false() {
        String notANumber = "125.5a";
        assertFalse(cpu.checkIfNumber(notANumber));
    }

    @Test
    public void test_if_convertToGHz_converts_correctly() {
        double cycles = 2066201729;
        double seconds = 0.957617512;
        double expectedGHz = 2.1576482291815062;
        assertEquals(expectedGHz, cpu.convertToGHz(cycles, seconds), DELTA);
    }

}
