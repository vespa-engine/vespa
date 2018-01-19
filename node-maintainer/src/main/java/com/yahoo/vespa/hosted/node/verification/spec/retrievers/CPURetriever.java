// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.commons.parser.OutputParser;
import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseInstructions;
import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseResult;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Retrieves number of CPU cores, and stores the result in a HardwareInfo instance
 *
 * @author olaaun
 * @author sgrostad
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

    @Override
    public void updateInfo() {
        try {
            List<String> commandOutput = commandExecutor.executeCommand(CPU_INFO_COMMAND);
            List<ParseResult> parseResults = parseCPUInfoFile(commandOutput);
            setCpuCores(parseResults);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to retrieve CPU info", e);
        }
    }

    List<ParseResult> parseCPUInfoFile(List<String> commandOutput) {
        List<String> searchWords = Collections.singletonList(SEARCH_WORD);
        ParseInstructions parseInstructions = new ParseInstructions(SEARCH_ELEMENT_INDEX, RETURN_ELEMENT_INDEX, REGEX_SPLIT, searchWords);
        return OutputParser.parseOutput(parseInstructions, commandOutput);
    }

    void setCpuCores(List<ParseResult> parseResults) {
        hardwareInfo.setMinCpuCores(countCpuCores(parseResults));
    }

    private int countCpuCores(List<ParseResult> parseResults) {
        return parseResults.size();
    }

}
