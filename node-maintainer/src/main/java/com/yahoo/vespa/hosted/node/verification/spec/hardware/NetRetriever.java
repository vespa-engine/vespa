package com.yahoo.vespa.hosted.node.verification.spec.hardware;

import com.yahoo.vespa.hosted.node.verification.spec.parse.ParseInstructions;
import com.yahoo.vespa.hosted.node.verification.spec.parse.OutputParser;
import com.yahoo.vespa.hosted.node.verification.spec.parse.ParseResult;

import java.io.*;
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
    private static final Logger logger = Logger.getLogger(NetRetriever.class.getName());
    private final HardwareInfo hardwareInfo;
    private final CommandExecutor commandExecutor;


    public NetRetriever(HardwareInfo hardwareInfo, CommandExecutor commandExecutor){
        this.hardwareInfo = hardwareInfo;
        this.commandExecutor = commandExecutor;
    }

    public void updateInfo() {
        try{
            ArrayList<ParseResult> parseResults = findInterface();
            findInterfaceSpeed(parseResults);
            updateHardwareInfoWithNet(parseResults);
        }
        catch (IOException e) {
            logger.log(Level.WARNING, "Failed to retrieve net info", e);
        }

    }

    protected ArrayList<ParseResult> findInterface() throws IOException{
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
        String regexSplit = "\\s+";
        String skipWord = "lo";
        String skipUntilWord = "";
        int searchElementIndex = 0;
        int returnElementIndex = 0;
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(SEARCH_WORD_INTERFACE_IP4, SEARCH_WORD_INTERFACE_IPV6, SEARCH_WORD_INTERFACE_NAME));
        ParseInstructions parseInstructions = new ParseInstructions(searchElementIndex, returnElementIndex, regexSplit, searchWords);
        parseInstructions.setSkipWord(skipWord);
        parseInstructions.setSkipUntilKeyword(skipUntilWord);
        ArrayList<ParseResult> parseResults = OutputParser.parseOutPutWithSkips(parseInstructions, commandOutput);
        return parseResults;
    }

    protected ParseResult parseInterfaceSpeed(ArrayList<String> commandOutput) {
        String regexSplit = ":";
        int searchElementIndex = 0;
        int returnElementIndex = 1;
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(SEARCH_WORD_INTERFACE_SPEED));
        ParseInstructions parseInstructions = new ParseInstructions(searchElementIndex, returnElementIndex, regexSplit, searchWords);
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
        for(ParseResult parseResult : parseResults) {
            switch (parseResult.getSearchWord()){
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
            }
        }
    }
}
