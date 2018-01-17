// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.commons.parser.OutputParser;
import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseInstructions;
import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseResult;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo.DiskType;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Retrieves disk space and type, and stores the result in a HardwareInfo instance
 *
 * @author olaaun
 * @author sgrostad
 */
public class DiskRetriever implements HardwareRetriever {

    private static final String DISK_CHECK_TYPE = "lsblk -d -o name,rota";
    private static final String DISK_CHECK_SIZE = "pvdisplay --units G | grep 'PV Size'";
    private static final String DISK_NAME = "sda";
    private static final String DISK_TYPE_REGEX_SPLIT = "\\s+";
    private static final int DISK_TYPE_SEARCH_ELEMENT_INDEX = 0;
    private static final int DISK_TYPE_RETURN_ELEMENT_INDEX = 1;
    private static final String DISK_SIZE_SEARCH_WORD = "Size";
    private static final String DISK_SIZE_REGEX_SPLIT = "\\s+";
    private static final int DISK_SIZE_SEARCH_ELEMENT_INDEX = 1;
    private static final int DISK_SIZE_RETURN_ELEMENT_INDEX = 2;
    private static final Logger logger = Logger.getLogger(DiskRetriever.class.getName());
    private final HardwareInfo hardwareInfo;
    private final CommandExecutor commandExecutor;


    public DiskRetriever(HardwareInfo hardwareInfo, CommandExecutor commandExecutor) {
        this.hardwareInfo = hardwareInfo;
        this.commandExecutor = commandExecutor;
    }

    @Override
    public void updateInfo() {
        updateDiskType();
        updateDiskSize();
    }

    void updateDiskType() {
        try {
            List<String> commandOutput = commandExecutor.executeCommand(DISK_CHECK_TYPE);
            ParseResult parseResult = parseDiskType(commandOutput);
            setDiskType(parseResult);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to retrieve disk type", e);
        }
    }

    void updateDiskSize() {
        try {
            List<String> commandOutput = commandExecutor.executeCommand(DISK_CHECK_SIZE);
            List<ParseResult> parseResult = parseDiskSize(commandOutput);
            setDiskSize(parseResult);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to retrieve disk size", e);
        }
    }

    ParseResult parseDiskType(List<String> commandOutput) throws IOException {
        List<String> searchWords = Collections.singletonList(DISK_NAME);
        ParseInstructions parseInstructions = new ParseInstructions(DISK_TYPE_SEARCH_ELEMENT_INDEX, DISK_TYPE_RETURN_ELEMENT_INDEX, DISK_TYPE_REGEX_SPLIT, searchWords);
        ParseResult parseResult = OutputParser.parseSingleOutput(parseInstructions, commandOutput);
        if (!parseResult.getSearchWord().equals(DISK_NAME)) {
            throw new IOException("Parsing for disk type failed");
        }
        return parseResult;
    }

    void setDiskType(ParseResult parseResult) {
        hardwareInfo.setDiskType(DiskType.UNKNOWN);
        String fastDiskSymbol = "0";
        String nonFastDiskSymbol = "1";
        if (parseResult.getValue().equals(fastDiskSymbol)) {
            hardwareInfo.setDiskType(DiskType.FAST);
        } else if (parseResult.getValue().equals(nonFastDiskSymbol)) {
            hardwareInfo.setDiskType(DiskType.SLOW);
        }
    }

    List<ParseResult> parseDiskSize(List<String> commandOutput) {
        List<String> searchWords = Collections.singletonList(DISK_SIZE_SEARCH_WORD);
        ParseInstructions parseInstructions = new ParseInstructions(DISK_SIZE_SEARCH_ELEMENT_INDEX, DISK_SIZE_RETURN_ELEMENT_INDEX, DISK_SIZE_REGEX_SPLIT, searchWords);
        return OutputParser.parseOutput(parseInstructions, commandOutput);
    }

    private void setDiskSize(List<ParseResult> parseResults) {
        double diskSize = 0;
        try {
            for (ParseResult parseResult : parseResults) {
                String sizeValue = parseResult.getValue().replaceAll("[^\\d.]", "");
                diskSize += Double.parseDouble(sizeValue);
            }
        } catch (NumberFormatException | NullPointerException e) {
            logger.log(Level.WARNING, "Parse results contained an invalid PV size - ", parseResults);
        } finally {
            hardwareInfo.setMinDiskAvailableGb(diskSize);
        }
    }

}
