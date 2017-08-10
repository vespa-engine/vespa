package com.yahoo.vespa.hosted.node.verification.hardware.benchmarks;

import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.commons.parser.OutputParser;
import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseInstructions;
import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by sgrostad on 11/07/2017.
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

    public void doBenchmark() {
        try {
            ArrayList<String> commandOutput = commandExecutor.executeCommand(CPU_BENCHMARK_COMMAND);
            ArrayList<ParseResult> parseResults = parseCpuCyclesPerSec(commandOutput);
            setCpuCyclesPerSec(parseResults);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to perform CPU benchmark", e);
        }
    }

    protected ArrayList<ParseResult> parseCpuCyclesPerSec(ArrayList<String> commandOutput) {
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(CYCLES_SEARCH_WORD, SECONDS_SEARCH_WORD));
        ParseInstructions parseInstructions = new ParseInstructions(SEARCH_ELEMENT_INDEX, RETURN_ELEMENT_INDEX, SPLIT_REGEX_STRING, searchWords);
        return OutputParser.parseOutput(parseInstructions, commandOutput);
    }


    protected void setCpuCyclesPerSec(ArrayList<ParseResult> parseResults) {
        double cpuCyclesPerSec = getCyclesPerSecond(parseResults);
        if (cpuCyclesPerSec > 0) {
            benchmarkResults.setCpuCyclesPerSec(cpuCyclesPerSec);
        }
    }

    protected double getCyclesPerSecond(ArrayList<ParseResult> parseResults) {
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

    protected double makeCyclesDouble(String cycles) {
        cycles = cycles.replaceAll("[^\\d]", "");
        if (checkIfNumber(cycles)) {
            return Double.parseDouble(cycles);
        }
        return -1;
    }

    protected double makeSecondsDouble(String seconds) {
        seconds = seconds.replaceAll(",", ".");
        if (checkIfNumber(seconds)) {
            return Double.parseDouble(seconds);
        }
        return -1;
    }

    protected boolean checkIfNumber(String numberCandidate) {
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

    protected double convertToGHz(double cycles, double seconds) {
        double giga = 1000000000.0;
        return (cycles / seconds) / giga;
    }

}
