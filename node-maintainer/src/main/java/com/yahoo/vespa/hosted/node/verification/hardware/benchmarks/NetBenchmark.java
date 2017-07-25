package com.yahoo.vespa.hosted.node.verification.hardware.benchmarks;

import com.yahoo.vespa.hosted.node.verification.commons.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by sgrostad on 11/07/2017.
 */
public class NetBenchmark implements Benchmark {

    private final String NET_BENCHMARK_COMMAND = "ping6 -c 10 www.yahoo.com | grep transmitted";
    private final String PING_SEARCH_WORD = "loss,";

    private final HardwareResults hardwareResults;
    private final CommandExecutor commandExecutor;

    public NetBenchmark(HardwareResults hardwareResults, CommandExecutor commandExecutor){
        this.hardwareResults = hardwareResults;
        this.commandExecutor = commandExecutor;
    }

    public void doBenchmark(){
        try {
            ArrayList<String> commandOutput = commandExecutor.executeCommand(NET_BENCHMARK_COMMAND);
            ParseResult parseResult = parsePingResponse(commandOutput);
            setIpv6Connectivity(parseResult);
        }
        catch (IOException e) {
            System.err.println(e);
        }
    }

    protected ParseResult parsePingResponse(ArrayList<String> commandOutput) {
        ArrayList<String> searchWords = new ArrayList<>(Arrays.asList(PING_SEARCH_WORD));
        String splitRegexString = "\\s+";
        int searchElementIndex = 7;
        int returnElementIndex = 5;
        ParseInstructions parseInstructions = new ParseInstructions(searchElementIndex, returnElementIndex, splitRegexString, searchWords);
        return OutputParser.parseSingleOutput(parseInstructions, commandOutput);

    }

    protected void setIpv6Connectivity(ParseResult parseResult) {
        if (parseResult.getSearchWord().equals(PING_SEARCH_WORD)) {
            String pingResponse = parseResult.getValue();
            String packetLoss = pingResponse.replaceAll("[^\\d.]", "");
            if (packetLoss.equals("")) return;
            if (Double.parseDouble(packetLoss) > 99) return;
            hardwareResults.setIpv6Connectivity(true);
        }
    }

}
