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
public class NetRetriever implements HardwareRetriever {
    private static final String NET_FIND_INTERFACE = "/sbin/ifconfig";
    private static final String NET_CHECK_INTERFACE_SPEED = "/sbin/ethtool";
    private static final String SEARCH_WORD_INTERFACE_IP4 = "inet";
    private static final String SEARCH_WORD_INTERFACE_IPV6 = "inet6";
    private static final String SEARCH_WORD_INTERFACE_NAME = "eth.";
    private static final String SEARCH_WORD_INTERFACE_SPEED = "Speed";
    private static final String INTERFACE_NAME_REGEX_SPLIT = "\\s+";
    private static final String INTERFACE_NAME_SKIP_WORD = "lo";
    private static final String INTERFACE_NAME_SKIP_UNTIL_WORD = "";
    private static final int INTERFACE_NAME_SEARCH_ELEMENT_INDEX = 0;
    private static final int INTERFACE_NAME_RETURN_ELEMENT_INDEX = 0;
    private static final String INTERFACE_SPEED_REGEX_SPLIT = ":";
    private static final int INTERFACE_SPEED_SEARCH_ELEMENT_INDEX = 0;
    private static final int INTERFACE_SPEED_RETURN_ELEMENT_INDEX = 1;
    private static final Logger logger = Logger.getLogger(NetRetriever.class.getName());
    private final HardwareInfo hardwareInfo;
    private final CommandExecutor commandExecutor;


    public NetRetriever(HardwareInfo hardwareInfo, CommandExecutor commandExecutor) {
        this.hardwareInfo = hardwareInfo;
        this.commandExecutor = commandExecutor;
    }

    public void updateInfo() {
        try {
            ArrayList<ParseResult> parseResults = findInterface();
            findInterfaceSpeed(parseResults);
            updateHardwareInfoWithNet(parseResults);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to retrieve net info", e);
        }
    }

    protected ArrayList<ParseResult> findInterface() throws IOException {
        ArrayList<String> commandOutput = commandExecutor.executeCommand(NET_FIND_INTERFACE);
        ArrayList<ParseResult> parseResults = parseNetInterface(commandOutput);
        return parseResults;
    }

    protected void findInterfaceSpeed(ArrayList<ParseResult> parseResults) throws IOException {
        String interfaceName = findInterfaceName(parseResults);
        String command = NET_CHECK_INTERFACE_SPEED + " " + interfaceName;
        ArrayList<String> commandOutput = commandExecutor.executeCommand(command);
        parseResults.add(parseInterfaceSpeed(commandOutput));
    }

    protected ArrayList<ParseResult> parseNetInterface(ArrayList<String> commandOutput) {
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(SEARCH_WORD_INTERFACE_IP4, SEARCH_WORD_INTERFACE_IPV6, SEARCH_WORD_INTERFACE_NAME));
        ParseInstructions parseInstructions = new ParseInstructions(INTERFACE_NAME_SEARCH_ELEMENT_INDEX, INTERFACE_NAME_RETURN_ELEMENT_INDEX, INTERFACE_NAME_REGEX_SPLIT, searchWords);
        parseInstructions.setSkipWord(INTERFACE_NAME_SKIP_WORD);
        parseInstructions.setSkipUntilKeyword(INTERFACE_NAME_SKIP_UNTIL_WORD);
        ArrayList<ParseResult> parseResults = OutputParser.parseOutPutWithSkips(parseInstructions, commandOutput);
        return parseResults;
    }

    protected ParseResult parseInterfaceSpeed(ArrayList<String> commandOutput) {
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(SEARCH_WORD_INTERFACE_SPEED));
        ParseInstructions parseInstructions = new ParseInstructions(INTERFACE_SPEED_SEARCH_ELEMENT_INDEX, INTERFACE_SPEED_RETURN_ELEMENT_INDEX, INTERFACE_SPEED_REGEX_SPLIT, searchWords);
        ParseResult parseResult = OutputParser.parseSingleOutput(parseInstructions, commandOutput);
        return parseResult;
    }

    protected String findInterfaceName(ArrayList<ParseResult> parseResults) {
        for (ParseResult parseResult : parseResults) {
            if (!parseResult.getSearchWord().matches(SEARCH_WORD_INTERFACE_NAME)) continue;
            return parseResult.getValue();
        }
        return "";
    }

    protected void updateHardwareInfoWithNet(ArrayList<ParseResult> parseResults) {
        hardwareInfo.setIpv6Connectivity(false);
        hardwareInfo.setIpv4Connectivity(false);
        for (ParseResult parseResult : parseResults) {
            switch (parseResult.getSearchWord()) {
                case SEARCH_WORD_INTERFACE_IP4:
                    hardwareInfo.setIpv4Connectivity(true);
                    break;
                case SEARCH_WORD_INTERFACE_IPV6:
                    hardwareInfo.setIpv6Connectivity(true);
                    break;
                case SEARCH_WORD_INTERFACE_SPEED:
                    String speedValue = parseResult.getValue().replaceAll("[^\\d.]", "");
                    double speed = Double.parseDouble(speedValue);
                    hardwareInfo.setInterfaceSpeedMbs(speed);
                    break;
                default:
                    if (parseResult.getSearchWord().matches(SEARCH_WORD_INTERFACE_NAME)) break;
                    throw new RuntimeException("Invalid ParseResult search word");
            }
        }
    }

}
