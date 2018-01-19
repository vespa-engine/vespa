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
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Responsible for benchmarking disk write speed, and storing the result in a BenchmarkResults instance
 *
 * @author olaaun
 * @author sgrostad
 */
public class DiskBenchmark implements Benchmark {

    private static final String DISK_BENCHMARK_COMMAND = "(dd if=/dev/zero of=/home/y/tmp/output conv=fdatasync bs=4G count=4; rm -f /home/y/tmp/output;) 2>&1 | grep bytes | awk  '{ print $8 \" \" $9 }'";
    private static final String KILO_BYTE_SEARCH_WORD = "kB/s";
    private static final String MEGA_BYTE_SEARCH_WORD = "MB/s";
    private static final String GIGA_BYTE_SEARCH_WORD = "GB/s";
    private static final String SPLIT_REGEX_STRING = " ";
    private static final int SEARCH_ELEMENT_INDEX = 1;
    private static final int RETURN_ELEMENT_INDEX = 0;
    private static final Logger logger = Logger.getLogger(DiskBenchmark.class.getName());
    private final BenchmarkResults benchmarkResults;
    private final CommandExecutor commandExecutor;

    public DiskBenchmark(BenchmarkResults benchmarkResults, CommandExecutor commandExecutor) {
        this.benchmarkResults = benchmarkResults;
        this.commandExecutor = commandExecutor;
    }

    @Override
    public void doBenchmark() {
        try {
            List<String> commandOutput = commandExecutor.executeCommand(DISK_BENCHMARK_COMMAND);
            Optional<ParseResult> parseResult = parseDiskSpeed(commandOutput);
            setDiskSpeed(parseResult);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to perform disk benchmark", e);
        }
    }

    Optional<ParseResult> parseDiskSpeed(List<String> commandOutput) {
        List<String> searchWords = new ArrayList<>(Arrays.asList(KILO_BYTE_SEARCH_WORD, MEGA_BYTE_SEARCH_WORD, GIGA_BYTE_SEARCH_WORD));
        ParseInstructions parseInstructions = new ParseInstructions(SEARCH_ELEMENT_INDEX, RETURN_ELEMENT_INDEX, SPLIT_REGEX_STRING, searchWords);
        return OutputParser.parseSingleOutput(parseInstructions, commandOutput);
    }

    void setDiskSpeed(Optional<ParseResult> parseResult) {
        benchmarkResults.setDiskSpeedMbs(parseResult.map(this::getDiskSpeedInMBs).orElse(0.0d));
    }

    double getDiskSpeedInMBs(ParseResult parseResult) {
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
                default:
                    throw new RuntimeException("Invalid ParseResult searchWord: " + parseResult.getSearchWord());
            }
        }

        return diskSpeedMBs;
    }

    boolean checkSpeedValidity(String speed) {
        try {
            Double.parseDouble(speed);
        } catch (NullPointerException | NumberFormatException e) {
            return false;
        }
        return true;
    }

    Double convertToMBs(String speed, double numberToConvert) {
        double speedMbs = Double.parseDouble(speed);
        return speedMbs * numberToConvert;
    }

}
