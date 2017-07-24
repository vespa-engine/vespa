package com.yahoo.vespa.hosted.node.verification.hardware.benchmarks;

import com.yahoo.vespa.hosted.node.verification.hardware.parse.ParseResult;
import com.yahoo.vespa.hosted.node.verification.hardware.mock.*;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * Created by olaa on 11/07/2017.
 */
public class NetBenchmarkTest {

    private HardwareResults hardwareResults;
    private NetBenchmark netBenchmark;
    private MockCommandExecutor commandExecutor;
    private String VALID_PING_RESPONSE = "src/test/java/com/yahoo/vespa/hosted/node/verification/hardware/resources/validpingresponse";
    private String INVALID_PING_RESPONSE = "src/test/java/com/yahoo/vespa/hosted/node/verification/hardware/resources/invalidpingresponse";
    private String CRAZY_PING_RESPONSE = "src/test/java/com/yahoo/vespa/hosted/node/verification/hardware/resources/crazypingresponse";

    @Before
    public void setup(){
        hardwareResults = new HardwareResults();
        commandExecutor = new MockCommandExecutor();
        netBenchmark = new NetBenchmark(hardwareResults, commandExecutor);
    }
    @Test
    public void test_doBenchmark_should_update_hardwareResults_to_true(){
        String mockCommand = "cat " + VALID_PING_RESPONSE;
        commandExecutor.addCommand(mockCommand);
        netBenchmark.doBenchmark();
        assertTrue(hardwareResults.isIpv6Connectivity());
    }
    @Test
    public void test_doBenchmark_should_update_hardwareResults_to_false_1(){
        String mockCommand = "cat " + INVALID_PING_RESPONSE;
        commandExecutor.addCommand(mockCommand);
        netBenchmark.doBenchmark();
        assertFalse(hardwareResults.isIpv6Connectivity());
    }

    @Test
    public void test_doBenchmark_should_update_hardwareResults_to_false_2(){
        String mockCommand = "cat " + CRAZY_PING_RESPONSE;
        commandExecutor.addCommand(mockCommand);
        netBenchmark.doBenchmark();
        assertFalse(hardwareResults.isIpv6Connectivity());
    }

    @Test
    public void test_parsePingResponse_valid_ping_response_should_return_ipv6_connectivity() throws IOException{
        String command = "src/test/java/com/yahoo/vespa/hosted/node/verification/hardware/resources/validpingresponse";
        ArrayList<String> mockCommandOutput = commandExecutor.readFromFile(command);
        ParseResult parseResult = netBenchmark.parsePingResponse(mockCommandOutput);
        String expectedPing = "0%";
        assertEquals(expectedPing, parseResult.getValue());
    }

    @Test
    public void test_parsePingResponse_invalid_ping_response_should_return_invalid_ParseResult() throws IOException{
        String command = "src/test/java/com/yahoo/vespa/hosted/node/verification/hardware/resources/crazypingresponse";
        ArrayList<String> mockCommandOutput =  commandExecutor.readFromFile(command);
        ParseResult parseResult = netBenchmark.parsePingResponse(mockCommandOutput);
        ParseResult expectedParseResult = new ParseResult("invalid", "invalid");
        assertEquals(expectedParseResult,parseResult);
    }

    @Test
    public void test_setIpv6Connectivity_valid_ping_response_should_return_ipv6_connectivity(){
        ParseResult parseResult = new ParseResult("loss,", "0%");
        netBenchmark.setIpv6Connectivity(parseResult);
        assertTrue(hardwareResults.isIpv6Connectivity());
    }
    @Test
    public void test_setIpv6Connectivity_invalid_ping_response_should_return_no_ipv6_connectivity_1(){
        ParseResult parseResult = new ParseResult("loss,", "100%");
        netBenchmark.setIpv6Connectivity(parseResult);
        assertFalse(hardwareResults.isIpv6Connectivity());
    }
    @Test
    public void test_setIpv6Connectivity_invalid_ping_response_should_return_no_ipv6_connectivity_2(){
        ParseResult parseResult = new ParseResult("loss,", "invalid");
        netBenchmark.setIpv6Connectivity(parseResult);
        assertFalse(hardwareResults.isIpv6Connectivity());
    }

}