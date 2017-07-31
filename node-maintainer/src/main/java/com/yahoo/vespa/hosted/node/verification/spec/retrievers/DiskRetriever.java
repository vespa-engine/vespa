package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.commons.OutputParser;
import com.yahoo.vespa.hosted.node.verification.commons.ParseInstructions;
import com.yahoo.vespa.hosted.node.verification.commons.ParseResult;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo.DiskType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by olaa on 30/06/2017.
 */
public class DiskRetriever implements HardwareRetriever {
    private static final String DISK_CHECK_TYPE = "lsblk -d -o name,rota";
    private static final String DISK_CHECK_SIZE = "df -BG | grep -v tmpfs | awk '{s+=$2} END {print s-1}'";
    private static final String DISK_NAME = "sda";
    private static final String DISK_TYPE_REGEX_SPLIT = "\\s+";
    private static final int DISK_TYPE_SEARCH_ELEMENT_INDEX = 0;
    private static final int DISK_TYPE_RETURN_ELEMENT_INDEX = 1;
    private static final String DISK_SIZE_SEARCH_WORD = ".*\\d+.*";
    private static final String DISK_SIZE_REGEX_SPLIT = "\\s+";
    private static final int DISK_SIZE_SEARCH_ELEMENT_INDEX = 0;
    private static final int DISK_SIZE_RETURN_ELEMENT_INDEX = 0;
    private static final Logger logger = Logger.getLogger(DiskRetriever.class.getName());
    private final HardwareInfo hardwareInfo;
    private final CommandExecutor commandExecutor;


    public DiskRetriever(HardwareInfo hardwareInfo, CommandExecutor commandExecutor) {
        this.hardwareInfo = hardwareInfo;
        this.commandExecutor = commandExecutor;
    }

    public void updateInfo() {
        try {
            updateDiskType();
            updateDiskSize();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to retrieve disk info", e);
        }
    }

    protected void updateDiskType() throws IOException {
        ArrayList<String> commandOutput = commandExecutor.executeCommand(DISK_CHECK_TYPE);
        ParseResult parseResult = parseDiskType(commandOutput);
        setDiskType(parseResult);
    }

    protected void updateDiskSize() throws IOException {
        ArrayList<String> commandOutput = commandExecutor.executeCommand(DISK_CHECK_SIZE);
        ParseResult parseResult = parseDiskSize(commandOutput);
        setDiskSize(parseResult);
    }

    protected ParseResult parseDiskType(ArrayList<String> commandOutput) {
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(DISK_NAME));
        ParseInstructions parseInstructions = new ParseInstructions(DISK_TYPE_SEARCH_ELEMENT_INDEX, DISK_TYPE_RETURN_ELEMENT_INDEX, DISK_TYPE_REGEX_SPLIT, searchWords);
        return OutputParser.parseSingleOutput(parseInstructions, commandOutput);
    }

    protected void setDiskType(ParseResult parseResult) {
        hardwareInfo.setDiskType(DiskType.UNKNOWN);
        if (!parseResult.getSearchWord().equals(DISK_NAME)) {
            return;
        }
        String fastDiskSymbol = "0";
        String nonFastDiskSymbol = "1";
        if (parseResult.getValue().equals(fastDiskSymbol)) {
            hardwareInfo.setDiskType(DiskType.FAST);
        } else if (parseResult.getValue().equals(nonFastDiskSymbol)) {
            hardwareInfo.setDiskType(DiskType.SLOW);
        }
    }

    protected ParseResult parseDiskSize(ArrayList<String> commandOutput) {
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(DISK_SIZE_SEARCH_WORD));
        ParseInstructions parseInstructions = new ParseInstructions(DISK_SIZE_SEARCH_ELEMENT_INDEX, DISK_SIZE_RETURN_ELEMENT_INDEX, DISK_SIZE_REGEX_SPLIT, searchWords);
        return OutputParser.parseSingleOutput(parseInstructions, commandOutput);
    }

    protected void setDiskSize(ParseResult parseResult) {
        try {
            String sizeValue = parseResult.getValue().replaceAll("[^\\d.]", "");
            double diskSize = Double.parseDouble(sizeValue);
            hardwareInfo.setMinDiskAvailableGb(diskSize);
        } catch (NumberFormatException | NullPointerException e) {
            return;
        }
    }

}