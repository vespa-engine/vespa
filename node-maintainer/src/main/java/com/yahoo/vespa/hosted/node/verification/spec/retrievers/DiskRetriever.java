package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.commons.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by olaa on 30/06/2017.
 */
public class DiskRetriever implements HardwareRetriever {
    private static final String DISK_CHECK_TYPE = "lsblk -d -o name,rota";
    private static final String DISK_CHECK_SIZE = "df -BG /";
    private final String DISK_NAME = "sda";
    private static final Logger logger = Logger.getLogger(DiskRetriever.class.getName());

    private final HardwareInfo hardwareInfo;
    private final CommandExecutor commandExecutor;


    public DiskRetriever(HardwareInfo hardwareInfo, CommandExecutor commandExecutor){
        this.hardwareInfo = hardwareInfo;
        this.commandExecutor = commandExecutor;
    }

    public void updateInfo() {
        try {
            updateDiskType();
            updateDiskSize();
        }
        catch(IOException e){
            logger.log(Level.WARNING, "Failed to retrieve disk info", e);
        }
    }

    protected void updateDiskType() throws IOException{
        ArrayList<String> commandOutput = commandExecutor.executeCommand(DISK_CHECK_TYPE);
        ParseResult parseResult = parseDiskType(commandOutput);
        setDiskType(parseResult);
    }

    protected void updateDiskSize() throws IOException{
        ArrayList<String> commandOutput = commandExecutor.executeCommand(DISK_CHECK_SIZE);
        ParseResult parseResult = parseDiskSize(commandOutput);
        setDiskSize(parseResult);
    }

    protected ParseResult parseDiskType (ArrayList<String> commandOutput) {
        String regexSplit = "\\s+";
        int searchElementIndex = 0;
        int returnElementIndex = 1;
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(DISK_NAME));
        ParseInstructions parseInstructions = new ParseInstructions(searchElementIndex, returnElementIndex, regexSplit, searchWords);
        return OutputParser.parseSingleOutput(parseInstructions, commandOutput);
    }

    protected void setDiskType (ParseResult parseResult) {
        if (!parseResult.getSearchWord().equals(DISK_NAME)) {
            return;
        }
        String fastDiskEnum = "0";
        String nonFastDiskEnum = "1";
        Boolean fastdisk = null;
        if (parseResult.getValue().equals(fastDiskEnum)) {
            fastdisk = true;
        } else if (parseResult.getValue().equals(nonFastDiskEnum)) {
            fastdisk = false;
        }
       hardwareInfo.setFastDisk(fastdisk);
    }

    protected void setDiskSize(ParseResult parseResult) {
        try {
            String sizeValue = parseResult.getValue().replaceAll("[^\\d.]", "");
            double diskSize = Double.parseDouble(sizeValue);
            hardwareInfo.setMinDiskAvailableGb(diskSize);
        }
        catch (NumberFormatException | NullPointerException e) {
            return;
        }
    }

    protected ParseResult parseDiskSize (ArrayList<String> commandOutput) {
        String searchWord = ".*\\d+.*";
        String regexSplit = "\\s+";
        int searchElementIndex = 3;
        int returnElementIndex = 1;
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(searchWord));
        ParseInstructions parseInstructions = new ParseInstructions(searchElementIndex, returnElementIndex, regexSplit, searchWords);
        return OutputParser.parseSingleOutput(parseInstructions, commandOutput);
    }

}