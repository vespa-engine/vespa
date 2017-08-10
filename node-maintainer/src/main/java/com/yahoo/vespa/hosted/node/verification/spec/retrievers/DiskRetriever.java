package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.commons.parser.OutputParser;
import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseInstructions;
import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseResult;
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
    private static final String DISK_CHECK_SIZE = "sudo pvdisplay --units G | grep 'PV Size'";
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

    public void updateInfo() {
        updateDiskType();
        updateDiskSize();
    }

    protected void updateDiskType() {
        try {
            ArrayList<String> commandOutput = commandExecutor.executeCommand(DISK_CHECK_TYPE);
            ParseResult parseResult = parseDiskType(commandOutput);
            setDiskType(parseResult);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to retrieve disk type", e);
        }
    }

    protected void updateDiskSize() {
        try {
            ArrayList<String> commandOutput = commandExecutor.executeCommand(DISK_CHECK_SIZE);
            ArrayList<ParseResult> parseResult = parseDiskSize(commandOutput);
            setDiskSize(parseResult);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to retrieve disk size", e);
        }
    }

    protected ParseResult parseDiskType(ArrayList<String> commandOutput) throws IOException {
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(DISK_NAME));
        ParseInstructions parseInstructions = new ParseInstructions(DISK_TYPE_SEARCH_ELEMENT_INDEX, DISK_TYPE_RETURN_ELEMENT_INDEX, DISK_TYPE_REGEX_SPLIT, searchWords);
        ParseResult parseResult = OutputParser.parseSingleOutput(parseInstructions, commandOutput);
        if (!parseResult.getSearchWord().equals(DISK_NAME)) {
            throw new IOException("Parsing for disk type failed");
        }
        return parseResult;
    }

    protected void setDiskType(ParseResult parseResult) {
        hardwareInfo.setDiskType(DiskType.UNKNOWN);
        String fastDiskSymbol = "0";
        String nonFastDiskSymbol = "1";
        if (parseResult.getValue().equals(fastDiskSymbol)) {
            hardwareInfo.setDiskType(DiskType.FAST);
        } else if (parseResult.getValue().equals(nonFastDiskSymbol)) {
            hardwareInfo.setDiskType(DiskType.SLOW);
        }
    }

    protected ArrayList<ParseResult> parseDiskSize(ArrayList<String> commandOutput) {
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(DISK_SIZE_SEARCH_WORD));
        ParseInstructions parseInstructions = new ParseInstructions(DISK_SIZE_SEARCH_ELEMENT_INDEX, DISK_SIZE_RETURN_ELEMENT_INDEX, DISK_SIZE_REGEX_SPLIT, searchWords);
        return OutputParser.parseOutput(parseInstructions, commandOutput);
    }

    protected void setDiskSize(ArrayList<ParseResult> parseResults) {
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