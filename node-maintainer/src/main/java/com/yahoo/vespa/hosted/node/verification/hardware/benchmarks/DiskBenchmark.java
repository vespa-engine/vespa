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
 * Created by olaa on 10/07/2017.
 */
public class DiskBenchmark implements Benchmark {

    private final String DISK_BENCHMARK_COMMAND = "time (dd if=/dev/zero of=/tmp/tempfile bs=16k count=16k > /dev/null; sync; rm /tmp/tempfile) 2>&1 | grep bytes | awk  '{ print $8 \" \" $9 }'";
    private final String KILO_BYTE_SEARCH_WORD = "kB/s";
    private final String MEGA_BYTE_SEARCH_WORD = "MB/s";
    private final String GIGA_BYTE_SEARCH_WORD = "GB/s";
    Logger logger = Logger.getLogger(DiskBenchmark.class.getName());
    private final HardwareResults hardwareResults;
    private final CommandExecutor commandExecutor;

    public DiskBenchmark(HardwareResults hardwareResults, CommandExecutor commandExecutor) {
        this.hardwareResults = hardwareResults;
        this.commandExecutor = commandExecutor;
    }

    public void doBenchmark() {
        try {
            ArrayList<String> commandOutput = commandExecutor.executeCommand(DISK_BENCHMARK_COMMAND);
            ParseResult parseResult = parseDiskSpeed(commandOutput);
            setDiskSpeed(parseResult);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to perform disk benchmark", e);
        }
    }

    protected ParseResult parseDiskSpeed(ArrayList<String> commandOutput) {
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(KILO_BYTE_SEARCH_WORD, MEGA_BYTE_SEARCH_WORD, GIGA_BYTE_SEARCH_WORD));
        String splitRegexString = " ";
        int searchElementIndex = 1;
        int returnElementIndex = 0;
        ParseInstructions parseInstructions = new ParseInstructions(searchElementIndex, returnElementIndex, splitRegexString, searchWords);
        return OutputParser.parseSingleOutput(parseInstructions, commandOutput);
    }

    protected void setDiskSpeed(ParseResult parseResult) {
        double diskSpeedMBs = getDiskSpeedInMBs(parseResult);
        hardwareResults.setDiskSpeedMbs(diskSpeedMBs);
    }

    protected double getDiskSpeedInMBs(ParseResult parseResult) {
        double diskSpeedMBs = 0;
        double convertKBsToMBs = 1 / 1000.0;
        double convertGBsToMBs = 1000.0;
        double convertMbsToMBs = 1.0;
        String diskSpeed = parseResult.getValue();
        if (checkSpeedValidity(diskSpeed)) {
            switch (parseResult.getSearchWord()) {
                case KILO_BYTE_SEARCH_WORD:
                    diskSpeedMBs = convertToMBs(diskSpeed, convertKBsToMBs);
                    break;
                case MEGA_BYTE_SEARCH_WORD:
                    diskSpeedMBs = convertToMBs(diskSpeed, convertMbsToMBs);
                    break;
                case GIGA_BYTE_SEARCH_WORD:
                    diskSpeedMBs = convertToMBs(diskSpeed, convertGBsToMBs);
                    break;
            }
        }

        return diskSpeedMBs;
    }

    protected boolean checkSpeedValidity(String speed) {
        try {
            Double.parseDouble(speed);
        } catch (NullPointerException | NumberFormatException e) {
            return false;
        }
        return true;
    }

    protected Double convertToMBs(String speed, double numberToConvert) {
        double speedMbs = Double.parseDouble(speed);
        return speedMbs * numberToConvert;
    }

}
