// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.commons.parser.OutputParser;
import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseInstructions;
import com.yahoo.vespa.hosted.node.verification.commons.parser.ParseResult;
import com.yahoo.vespa.hosted.node.verification.spec.VerifierSettings;
import org.apache.commons.exec.ExecuteException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Retrieves IPv4/IPv6 interface, and checks interface speed. If node should have IPv6, tries to ping6.
 * The results are stored in a HardwareInfo instance
 *
 * @author olaaun
 * @author sgrostad
 */
public class NetRetriever implements HardwareRetriever {

    // Interface commands ignores lo-, veth- and docker interfaces
    private static final String NET_FIND_INTERFACE = "/sbin/ifconfig | awk 'BEGIN {RS=\"\\n\\n\"; } { if ( $1 != \"lo\" && !match($1, \"^veth\") && !match($1, \"^docker\")) {print} }'";
    private static final String NET_CHECK_INTERFACE_SPEED = "for i in $(/sbin/ifconfig | awk 'BEGIN {RS=\"\\n\\n\"; } { if ( $1 != \"lo\" && !match($1, \"^veth\") && !match($1, \"^docker\")) {print $1} }'); do /sbin/ethtool $i; done;";
    private static final String SEARCH_WORD_INTERFACE_IP4 = "inet";
    private static final String SEARCH_WORD_INTERFACE_IPV6 = "inet6";
    private static final String SEARCH_WORD_INTERFACE_SPEED = "Speed";
    private static final String INTERFACE_NAME_REGEX_SPLIT = "\\s+";
    private static final int INTERFACE_SEARCH_ELEMENT_INDEX = 0;
    private static final int INTERFACE_RETURN_ELEMENT_INDEX = 0;
    private static final String INTERFACE_SPEED_REGEX_SPLIT = ":";
    private static final int INTERFACE_SPEED_SEARCH_ELEMENT_INDEX = 0;
    private static final int INTERFACE_SPEED_RETURN_ELEMENT_INDEX = 1;
    private static final String PING_NET_COMMAND = "ping6 -c 1 -q www.yahoo.com | grep -oP '\\d+(?=% packet loss)'";
    private static final String PING_SEARCH_WORD = "\\d+\\.?\\d*";
    private static final String PING_SPLIT_REGEX_STRING = "\\s+";
    private static final int PING_SEARCH_ELEMENT_INDEX = 0;
    private static final int PING_RETURN_ELEMENT_INDEX = 0;
    private static final Logger logger = Logger.getLogger(NetRetriever.class.getName());
    private final HardwareInfo hardwareInfo;
    private final CommandExecutor commandExecutor;
    private final VerifierSettings verifierSettings;


    public NetRetriever(HardwareInfo hardwareInfo, CommandExecutor commandExecutor, VerifierSettings verifierSettings) {
        this.hardwareInfo = hardwareInfo;
        this.commandExecutor = commandExecutor;
        this.verifierSettings = verifierSettings;
    }

    @Override
    public void updateInfo() {
        List<ParseResult> parseResults = findInterface();
        findInterfaceSpeed(parseResults);
        if (verifierSettings.isCheckIPv6()) {
            testPingResponse(parseResults);
        }
        updateHardwareInfoWithNet(parseResults);
    }

    List<ParseResult> findInterface() {
        List<ParseResult> parseResults = new ArrayList<>();
        try {
            List<String> commandOutput = commandExecutor.executeCommand(NET_FIND_INTERFACE);
            parseResults = parseNetInterface(commandOutput);

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to retrieve net interface. ", e);
        }
        return parseResults;
    }

    List<ParseResult> parseNetInterface(List<String> commandOutput) {
        List<String> searchWords = new ArrayList<>(Arrays.asList(SEARCH_WORD_INTERFACE_IP4, SEARCH_WORD_INTERFACE_IPV6));
        ParseInstructions parseInstructions = new ParseInstructions(INTERFACE_SEARCH_ELEMENT_INDEX, INTERFACE_RETURN_ELEMENT_INDEX, INTERFACE_NAME_REGEX_SPLIT, searchWords);
        return OutputParser.parseOutput(parseInstructions, commandOutput);
    }

    void findInterfaceSpeed(List<ParseResult> parseResults) {
        try {
            List<String> commandOutput = commandExecutor.executeCommand(NET_CHECK_INTERFACE_SPEED);
            ParseResult parseResult = parseInterfaceSpeed(commandOutput);
            parseResults.add(parseResult);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to retrieve interface speed. ", e);
        }
    }

    ParseResult parseInterfaceSpeed(List<String> commandOutput) throws IOException {
        List<String> searchWords = Collections.singletonList(SEARCH_WORD_INTERFACE_SPEED);
        ParseInstructions parseInstructions = new ParseInstructions(INTERFACE_SPEED_SEARCH_ELEMENT_INDEX, INTERFACE_SPEED_RETURN_ELEMENT_INDEX, INTERFACE_SPEED_REGEX_SPLIT, searchWords);
        ParseResult parseResult = OutputParser.parseSingleOutput(parseInstructions, commandOutput);
        if (!parseResult.getSearchWord().matches(SEARCH_WORD_INTERFACE_SPEED)) {
            throw new IOException("Failed to parse interface speed output.");
        }
        return parseResult;
    }

    private void testPingResponse(List<ParseResult> parseResults) {
        try {
            List<String> commandOutput = commandExecutor.executeCommand(PING_NET_COMMAND);
            parseResults.add(parsePingResponse(commandOutput));
        } catch (ExecuteException e) {
            logger.log(Level.WARNING, "Failed to execute ping6", e);
        } catch (IOException e) {
            logger.log(Level.WARNING, e.getMessage());
        }
    }

    ParseResult parsePingResponse(List<String> commandOutput) throws IOException {
        List<String> searchWords = Collections.singletonList(PING_SEARCH_WORD);
        ParseInstructions parseInstructions = new ParseInstructions(PING_SEARCH_ELEMENT_INDEX, PING_RETURN_ELEMENT_INDEX, PING_SPLIT_REGEX_STRING, searchWords);
        ParseResult parseResult = OutputParser.parseSingleOutput(parseInstructions, commandOutput);
        if (!parseResult.getSearchWord().matches(PING_SEARCH_WORD)) {
            throw new IOException("Failed to parse ping output.");
        }
        return new ParseResult(PING_SEARCH_WORD, parseResult.getValue());
    }

    void updateHardwareInfoWithNet(List<ParseResult> parseResults) {
        hardwareInfo.setIpv6Interface(false);
        hardwareInfo.setIpv4Interface(false);
        for (ParseResult parseResult : parseResults) {
            switch (parseResult.getSearchWord()) {
                case SEARCH_WORD_INTERFACE_IP4:
                    hardwareInfo.setIpv4Interface(true);
                    break;
                case SEARCH_WORD_INTERFACE_IPV6:
                    hardwareInfo.setIpv6Interface(true);
                    break;
                case SEARCH_WORD_INTERFACE_SPEED:
                    double speed = convertInterfaceSpeed(parseResult.getValue());
                    hardwareInfo.setInterfaceSpeedMbs(speed);
                    break;
                case PING_SEARCH_WORD:
                    setIpv6Connectivity(parseResult);
                    break;
                default:
                    throw new RuntimeException("Invalid ParseResult search word: " + parseResult.getSearchWord());
            }
        }
    }

    double convertInterfaceSpeed(String speed) {
        return Double.parseDouble(speed.replaceAll("[^\\d.]", ""));
    }

    void setIpv6Connectivity(ParseResult parseResult) {
        String pingResponse = parseResult.getValue();
        String packetLoss = pingResponse.replaceAll("[^\\d.]", "");
        if (packetLoss.equals("")) return;
        if (Double.parseDouble(packetLoss) > 99) return;
        hardwareInfo.setIpv6Connection(true);
    }

}
