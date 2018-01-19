// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.hardware.benchmarks;

import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.commons.parser.OutputParser;
import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseInstructions;
import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Responsible for benchmarking CPU clock frequency, and storing the result in a BenchmarkResults instance
 *
 * @author sgrostad
 * @author olaaun
 */
public class CPUBenchmark implements Benchmark {

    private static final String CPU_BENCHMARK_COMMAND = "perf stat -e cycles dd if=/dev/zero of=/dev/null count=2000000 2>&1 | grep 'cycles\\|seconds'";
    private static final String CYCLES_SEARCH_WORD = "cycles";
    private static final String SECONDS_SEARCH_WORD = "seconds";
    private static final String SPLIT_REGEX_STRING = "\\s+";
    private static final int SEARCH_ELEMENT_INDEX = 1;
    private static final int RETURN_ELEMENT_INDEX = 0;
    private static final Logger logger = Logger.getLogger(CPUBenchmark.class.getName());
    private final BenchmarkResults benchmarkResults;

    private final CommandExecutor commandExecutor;

    public CPUBenchmark(BenchmarkResults benchmarkResults, CommandExecutor commandExecutor) {
        this.benchmarkResults = benchmarkResults;
        this.commandExecutor = commandExecutor;
    }

    @Override
    public void doBenchmark() {
        try {
            List<String> commandOutput = commandExecutor.executeCommand(CPU_BENCHMARK_COMMAND);
            List<ParseResult> parseResults = parseCpuCyclesPerSec(commandOutput);
            setCpuCyclesPerSec(parseResults);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to perform CPU benchmark", e);
        }
    }

    List<ParseResult> parseCpuCyclesPerSec(List<String> commandOutput) {
        List<String> searchWords = new ArrayList<>(Arrays.asList(CYCLES_SEARCH_WORD, SECONDS_SEARCH_WORD));
        ParseInstructions parseInstructions = new ParseInstructions(SEARCH_ELEMENT_INDEX, RETURN_ELEMENT_INDEX, SPLIT_REGEX_STRING, searchWords);
        return OutputParser.parseOutput(parseInstructions, commandOutput);
    }


    void setCpuCyclesPerSec(List<ParseResult> parseResults) {
        double cpuCyclesPerSec = getCyclesPerSecond(parseResults);
        if (cpuCyclesPerSec > 0) {
            benchmarkResults.setCpuCyclesPerSec(cpuCyclesPerSec);
        }
    }

    private double getCyclesPerSecond(List<ParseResult> parseResults) {
        double cycles = -1;
        double seconds = -1;
        for (ParseResult parseResult : parseResults) {
            switch (parseResult.getSearchWord()) {
                case CYCLES_SEARCH_WORD:
                    cycles = makeCyclesDouble(parseResult.getValue());
                    break;
                case SECONDS_SEARCH_WORD:
                    seconds = makeSecondsDouble(parseResult.getValue());
                    break;
                default:
                    throw new RuntimeException("Invalid ParseResult searchWord: " + parseResult.getSearchWord());
            }
        }
        if (cycles > 0 && seconds > 0) {
            return convertToGHz(cycles, seconds);
        }
        return -1;
    }

    double makeCyclesDouble(String cycles) {
        cycles = cycles.replaceAll("[^\\d]", "");
        if (checkIfNumber(cycles)) {
            return Double.parseDouble(cycles);
        }
        return -1;
    }

    double makeSecondsDouble(String seconds) {
        seconds = seconds.replaceAll(",", ".");
        if (checkIfNumber(seconds)) {
            return Double.parseDouble(seconds);
        }
        return -1;
    }

    boolean checkIfNumber(String numberCandidate) {
        if (numberCandidate == null || numberCandidate.equals("")) {
            return false;
        }
        try {
            Double.parseDouble(numberCandidate);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    double convertToGHz(double cycles, double seconds) {
        double giga = 1000000000.0;
        return (cycles / seconds) / giga;
    }

}
