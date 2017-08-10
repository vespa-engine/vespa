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
public class MemoryBenchmark implements Benchmark {

    private static final String MEM_BENCHMARK_CREATE_FOLDER = "mkdir -p RAM_test";
    private static final String MEM_BENCHMARK_MOUNT_TMPFS = "mount tmpfs -t tmpfs RAM_test/";
    private static final String MEM_BENCHMARK_UNMOUNT_TMPFS = "umount RAM_test";
    private static final String MEM_BENCHMARK_DELETE_FOLDER = "rm -rf RAM_test";
    private static final String MEM_BENCHMARK_WRITE_SPEED = "dd if=/dev/zero of=RAM_test/data_tmp bs=1M count=512";
    private static final String MEM_BENCHMARK_READ_SPEED = "dd if=RAM_test/data_tmp of=/dev/null bs=1M count=512";
    private static final String READ_AND_WRITE_SEARCH_WORD = "GB/s";
    private static final String SPLIT_REGEX_STRING = " ";
    private static final int SEARCH_ELEMENT_INDEX = 8;
    private static final int RETURN_ELEMENT_INDEX = 7;
    private static final Logger logger = Logger.getLogger(MemoryBenchmark.class.getName());
    private final BenchmarkResults benchmarkResults;
    private final CommandExecutor commandExecutor;

    public MemoryBenchmark(BenchmarkResults benchmarkResults, CommandExecutor commandExecutor) {
        this.benchmarkResults = benchmarkResults;
        this.commandExecutor = commandExecutor;
    }

    public void doBenchmark() {
        try {
            setupMountPoint();
            ArrayList<String> commandOutput = commandExecutor.executeCommand(MEM_BENCHMARK_WRITE_SPEED);
            ParseResult parseResult = parseMemorySpeed(commandOutput);
            updateMemoryWriteSpeed(parseResult.getValue());
            commandOutput = commandExecutor.executeCommand(MEM_BENCHMARK_READ_SPEED);
            parseResult = parseMemorySpeed(commandOutput);
            updateMemoryReadSpeed(parseResult.getValue());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to perform memory benchmark", e);
        } finally {
            breakDownMountPoint();
        }
    }

    private void setupMountPoint() throws IOException {
        commandExecutor.executeCommand(MEM_BENCHMARK_CREATE_FOLDER);
        commandExecutor.executeCommand(MEM_BENCHMARK_MOUNT_TMPFS);
    }

    private void breakDownMountPoint() {
        try {
            commandExecutor.executeCommand(MEM_BENCHMARK_UNMOUNT_TMPFS);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to unmount tmpfs folder", e);
        }
        try {
            commandExecutor.executeCommand(MEM_BENCHMARK_DELETE_FOLDER);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to delete memory benchmark folder", e);
        }
    }

    protected ParseResult parseMemorySpeed(ArrayList<String> commandOutput) {
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(READ_AND_WRITE_SEARCH_WORD));
        ParseInstructions parseInstructions = new ParseInstructions(SEARCH_ELEMENT_INDEX, RETURN_ELEMENT_INDEX, SPLIT_REGEX_STRING, searchWords);
        return OutputParser.parseSingleOutput(parseInstructions, commandOutput);
    }

    protected void updateMemoryWriteSpeed(String memorySpeed) {
        if (!isValidMemory(memorySpeed)) return;
        double memoryWriteSpeedGbs = Double.parseDouble(memorySpeed);
        benchmarkResults.setMemoryWriteSpeedGBs(memoryWriteSpeedGbs);
    }

    protected void updateMemoryReadSpeed(String memorySpeed) {
        if (!isValidMemory(memorySpeed)) return;
        double memoryReadSpeedGbs = Double.parseDouble(memorySpeed);
        benchmarkResults.setMemoryReadSpeedGBs(memoryReadSpeedGbs);
    }

    protected boolean isValidMemory(String benchmarkOutput) {
        try {
            Double.parseDouble(benchmarkOutput);
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
        return true;
    }

}
