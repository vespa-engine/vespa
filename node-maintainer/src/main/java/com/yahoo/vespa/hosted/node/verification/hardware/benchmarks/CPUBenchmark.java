package com.yahoo.vespa.hosted.node.verification.hardware.benchmarks;

import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.commons.OutputParser;
import com.yahoo.vespa.hosted.node.verification.commons.ParseInstructions;
import com.yahoo.vespa.hosted.node.verification.commons.ParseResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by sgrostad on 11/07/2017.
 */
public class CPUBenchmark implements Benchmark {

    private final String CPU_BENCHMARK_COMMAND = "perf stat -e cycles dd if=/dev/zero of=/dev/null count=100000 2>&1 | grep 'cycles\\|seconds'";
    private final String CYCLES_SEARCH_WORD = "cycles";
    private final String SECONDS_SEARCH_WORD = "seconds";
    private static final Logger logger = Logger.getLogger(CPUBenchmark.class.getName());

    private final HardwareResults hardwareResults;
    private final CommandExecutor commandExecutor;

    public CPUBenchmark(HardwareResults hardwareResults, CommandExecutor commandExecutor) {
        this.hardwareResults = hardwareResults;
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
        String splitRegexString = "\\s+";
        int searchElementIndex = 1;
        int returnElementIndex = 0;
        ParseInstructions parseInstructions = new ParseInstructions(searchElementIndex, returnElementIndex, splitRegexString, searchWords);
        return OutputParser.parseOutput(parseInstructions, commandOutput);
    }


    protected void setCpuCyclesPerSec(ArrayList<ParseResult> parseResults) {
        double cpuCyclesPerSec = getCyclesPerSecond(parseResults);
        if (cpuCyclesPerSec > 0) {
            hardwareResults.setCpuCyclesPerSec(cpuCyclesPerSec);
        }
    }

    protected double getCyclesPerSecond(ArrayList<ParseResult> parseResults) {
        double cycles = 0;
        double seconds = 0;
        for (ParseResult parseResult : parseResults) {
            switch (parseResult.getSearchWord()) {
                case CYCLES_SEARCH_WORD:
                    cycles = makeCyclesDouble(parseResult.getValue());
                    break;
                case SECONDS_SEARCH_WORD:
                    seconds = makeSecondsDouble(parseResult.getValue());
                    break;
            }
        }
        if (cycles != 0 && seconds != 0) {
            return convertToGHz(cycles, seconds);
        }
        return 0;
    }

    protected double makeCyclesDouble(String cycles) {
        cycles = cycles.replaceAll("[^\\d]", "");
        if (checkIfNumber(cycles)) {
            return Double.parseDouble(cycles);
        }
        return 0;
    }

    protected double makeSecondsDouble(String seconds) {
        seconds = seconds.replaceAll(",", ".");
        if (checkIfNumber(seconds)) {
            return Double.parseDouble(seconds);
        }
        return 0;
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
