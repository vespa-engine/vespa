package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

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
 * Created by olaa on 30/06/2017.
 */
public class CPURetriever implements HardwareRetriever {

    private static final String CPU_INFO_COMMAND = "cat /proc/cpuinfo";
    private static final String SEARCH_WORD = "cpu MHz";
    private static final String REGEX_SPLIT = "\\s+:\\s";
    private static final int SEARCH_ELEMENT_INDEX = 0;
    private static final int RETURN_ELEMENT_INDEX = 1;
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
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(SEARCH_WORD));
        ParseInstructions parseInstructions = new ParseInstructions(SEARCH_ELEMENT_INDEX, RETURN_ELEMENT_INDEX, REGEX_SPLIT, searchWords);
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
