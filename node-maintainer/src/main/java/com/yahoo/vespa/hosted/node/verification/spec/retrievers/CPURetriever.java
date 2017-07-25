package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

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
 * Created by olaa on 30/06/2017.
 */
public class CPURetriever implements HardwareRetriever {

    private static final String CPU_INFO_COMMAND = "cat /proc/cpuinfo";
    final static String searchWord = "cpu MHz";
    final static String regexSplit = "\\s+:\\s";
    final static int searchElementIndex = 0;
    final static int returnElementIndex = 1;
    private static final Logger logger = Logger.getLogger(CPURetriever.class.getName());

    private final HardwareInfo hardwareInfo;
    private final CommandExecutor commandExecutor;

    public CPURetriever(HardwareInfo hardwareInfo, CommandExecutor commandExecutor) {
        this.hardwareInfo = hardwareInfo;
        this.commandExecutor = commandExecutor;
    }

    public void updateInfo() {
        try {
            ArrayList<String> commandOutput = commandExecutor.executeCommand(CPU_INFO_COMMAND);
            ArrayList<ParseResult> parseResults = parseCPUInfoFile(commandOutput);
            setCpuCores(parseResults);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to retrieve CPU info", e);
        }
    }

    protected ArrayList<ParseResult> parseCPUInfoFile(ArrayList<String> commandOutput) {
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(searchWord));
        ParseInstructions parseInstructions = new ParseInstructions(searchElementIndex, returnElementIndex, regexSplit, searchWords);
        ArrayList<ParseResult> parseResults = OutputParser.parseOutput(parseInstructions, commandOutput);
        return parseResults;
    }

    protected void setCpuCores(ArrayList<ParseResult> parseResults) {
        hardwareInfo.setMinCpuCores(countCpuCores(parseResults));
    }

    protected int countCpuCores(ArrayList<ParseResult> parseResults) {
        return parseResults.size();
    }
}
