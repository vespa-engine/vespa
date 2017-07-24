package com.yahoo.vespa.hosted.node.verification.hardware.benchmarks;

import com.yahoo.vespa.hosted.node.verification.hardware.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.hardware.parse.ParseInstructions;
import com.yahoo.vespa.hosted.node.verification.hardware.parse.OutputParser;
import com.yahoo.vespa.hosted.node.verification.hardware.parse.ParseResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by sgrostad on 11/07/2017.
 */
public class MemoryBenchmark implements Benchmark {

    private static final String MEM_BENCHMARK_CREATE_FOLDER = "mkdir -p RAM_test";
    private static final String MEM_BENCHMARK_MOUNT_TMPFS = "sudo mount tmpfs -t tmpfs RAM_test/";
    private static final String MEM_BENCHMARK_UNMOUNT_TMPFS = "umount RAM_test";
    private static final String MEM_BENCHMARK_DELETE_FOLDER = "rm -rf RAM_test";
    private static final String MEM_BENCHMARK_WRITE_SPEED = "dd if=/dev/zero of=RAM_test/data_tmp bs=1M count=512";
    private static final String MEM_BENCHMARK_READ_SPEED = "dd if=RAM_test/data_tmp of=/dev/null bs=1M count=512";
    private final String READ_AND_WRITE_SEARCH_WORD = "GB/s";
    private final HardwareResults hardwareResults;
    private final CommandExecutor commandExecutor;

    public MemoryBenchmark(HardwareResults hardwareResults, CommandExecutor commandExecutor){
        this.hardwareResults = hardwareResults;
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
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            breakDownMountPoint();
        }
    }

    private void setupMountPoint() throws IOException{
        commandExecutor.executeCommand(MEM_BENCHMARK_CREATE_FOLDER);
        commandExecutor.executeCommand(MEM_BENCHMARK_MOUNT_TMPFS);
    }

    private void breakDownMountPoint() {
        try {
            commandExecutor.executeCommand(MEM_BENCHMARK_UNMOUNT_TMPFS);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        try {
            commandExecutor.executeCommand(MEM_BENCHMARK_DELETE_FOLDER);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected ParseResult parseMemorySpeed(ArrayList<String> commandOutput) {
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(READ_AND_WRITE_SEARCH_WORD));
        String splitRegexString = " ";
        int searchElementIndex = 8;
        int returnElementIndex = 7;
        ParseInstructions parseInstructions = new ParseInstructions(searchElementIndex, returnElementIndex, splitRegexString, searchWords);
        return OutputParser.parseSingleOutput(parseInstructions, commandOutput);
    }

    protected void updateMemoryWriteSpeed(String memorySpeed){
        if(!isValidMemory(memorySpeed)) return;
        double memoryWriteSpeedGbs = Double.parseDouble(memorySpeed);
        hardwareResults.setMemoryWriteSpeedGBs(memoryWriteSpeedGbs);
    }

    protected void updateMemoryReadSpeed(String memorySpeed) {
        if(!isValidMemory(memorySpeed)) return;
        double memoryReadSpeedGbs = Double.parseDouble(memorySpeed);
        hardwareResults.setMemoryReadSpeedGBs(memoryReadSpeedGbs);
    }

    protected boolean isValidMemory(String benchmarkOutput) {
        try {
             Double.parseDouble(benchmarkOutput);
        }
        catch (NumberFormatException | NullPointerException e) {
            return false;
        }
        return true;
    }
}
