package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.commons.ParseResult;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.VerifierSettings;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Created by sgrostad on 07/07/2017.
 */
public class NetRetrieverTest {

    private static final String RESOURCE_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/";
    private static final String NET_FIND_INTERFACE = RESOURCE_PATH + "ifconfig";
    private static final String NET_CHECK_INTERFACE_SPEED = RESOURCE_PATH + "eth0";
    private static String VALID_PING_RESPONSE = RESOURCE_PATH + "validpingresponse";
    private static String INVALID_PING_RESPONSE = RESOURCE_PATH + "invalidpingresponse";
    private static String PING_SEARCH_WORD = "\\d+\\.?\\d*";
    private HardwareInfo hardwareInfo;
    private MockCommandExecutor commandExecutor;
    private NetRetriever net;
    private ArrayList<ParseResult> parseResults;
    private VerifierSettings verifierSettings = spy(new VerifierSettings());
    private static final double DELTA = 0.1;

    @Before
    public void setup() {
        hardwareInfo = new HardwareInfo();
        commandExecutor = new MockCommandExecutor();
        doReturn(true).when(verifierSettings).isCheckIPv6();
        net = new NetRetriever(hardwareInfo, commandExecutor, verifierSettings);
        parseResults = new ArrayList<>();
    }

    @Test
    public void updateInfo_should_store_ipv4_ipv6_interface_and_interface_speed() {
        commandExecutor.addCommand("cat " + NET_FIND_INTERFACE);
        commandExecutor.addCommand("cat " + NET_CHECK_INTERFACE_SPEED);
        commandExecutor.addCommand("cat " + VALID_PING_RESPONSE);
        net.updateInfo();
        assertTrue(hardwareInfo.getIpv4Interface());
        assertTrue(hardwareInfo.getIpv6Interface());
        assertTrue(hardwareInfo.isIpv6Connection());
        double expectedInterfaceSpeed = 1000;
        assertEquals(expectedInterfaceSpeed, hardwareInfo.getInterfaceSpeedMbs(), DELTA);
    }

    @Test
    public void findInterfaceSpeed_valid_input() throws IOException {
        commandExecutor.addCommand("cat " + NET_FIND_INTERFACE);
        commandExecutor.addCommand("cat " + NET_CHECK_INTERFACE_SPEED);
        parseResults = net.findInterface();
        net.findInterfaceSpeed(parseResults);
        ParseResult expectedParseResults = new ParseResult("Speed", "1000Mb/s");
        assertEquals(expectedParseResults, parseResults.get(2));
    }

    @Test
    public void parseNetInterface_get_ipv_from_ifconfig_testFile() throws IOException {
        ArrayList<String> mockOutput = MockCommandExecutor.readFromFile(NET_FIND_INTERFACE);
        parseResults = net.parseNetInterface(mockOutput);
        net.updateHardwareInfoWithNet(parseResults);
        assertTrue(hardwareInfo.getIpv4Interface());
        assertTrue(hardwareInfo.getIpv6Interface());
    }

    @Test
    public void parseNetInterface_get_ipv_from_ifconfigNotIpv6_testFile() throws IOException {
        ArrayList<String> mockOutput = MockCommandExecutor.readFromFile(NET_FIND_INTERFACE + "NoIpv6");
        parseResults = net.parseNetInterface(mockOutput);
        ArrayList<ParseResult> expextedParseResults = new ArrayList<>(Arrays.asList(
                new ParseResult("inet", "inet")));
        assertEquals(expextedParseResults, parseResults);
    }

    @Test
    public void parseInterfaceSpeed_get_interfaceSpeed_from_eth0_testFile() throws IOException {
        ArrayList<String> mockOutput = MockCommandExecutor.readFromFile("src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/eth0");
        ParseResult parseResult = net.parseInterfaceSpeed(mockOutput);
        ParseResult expectedParseResult = new ParseResult("Speed", "1000Mb/s");
        assertEquals(expectedParseResult, parseResult);
    }


    @Test
    public void updateHardwareinfoWithNet_valid_input() {
        parseResults.add(new ParseResult("inet", "inet"));
        parseResults.add(new ParseResult("inet6", "inet6"));
        parseResults.add(new ParseResult("Speed", "1000Mb/s"));
        net.updateHardwareInfoWithNet(parseResults);
        double expectedInterfaceSpeed = 1000;
        assertEquals(expectedInterfaceSpeed, hardwareInfo.getInterfaceSpeedMbs(), DELTA);
        assertTrue(hardwareInfo.getIpv4Interface());
        assertTrue(hardwareInfo.getIpv6Interface());
    }

    @Test
    public void stripInterfaceSpeed_should_return_correct_double() {
        String interfaceSpeedToConvert = "1000Mb/s";
        double expectedInterfaceSpeed = 1000;
        double actualInterfaceSpeed = net.convertInterfaceSpeed(interfaceSpeedToConvert);
        assertEquals(expectedInterfaceSpeed, actualInterfaceSpeed, DELTA);
    }

    @Test
    public void parsePingResponse_valid_ping_response_should_return_ipv6_connectivity() throws IOException {
        ArrayList<String> mockCommandOutput = MockCommandExecutor.readFromFile(VALID_PING_RESPONSE);
        ParseResult parseResult = net.parsePingResponse(mockCommandOutput);
        String expectedPing = "0";
        assertEquals(expectedPing, parseResult.getValue());
    }

    @Test
    public void parsePingResponse_invalid_ping_response_should_throw_IOException() throws IOException {
        ArrayList<String> mockCommandOutput = MockCommandExecutor.readFromFile(INVALID_PING_RESPONSE);
        try {
            ParseResult parseResult = net.parsePingResponse(mockCommandOutput);
            fail("Expected an IOException to be thrown");
        } catch (IOException e) {
            String expectedExceptionMessage = "Failed to parse ping output.";
            assertEquals(expectedExceptionMessage, e.getMessage());
        }
    }

    @Test
    public void setIpv6Connectivity_valid_ping_response_should_return_ipv6_connectivity() {
        ParseResult parseResult = new ParseResult(PING_SEARCH_WORD, "0");
        net.setIpv6Connectivity(parseResult);
        assertTrue(hardwareInfo.isIpv6Connection());
    }

    @Test
    public void setIpv6Connectivity_invalid_ping_response_should_return_no_ipv6_connectivity_1() {
        ParseResult parseResult = new ParseResult(PING_SEARCH_WORD, "100");
        net.setIpv6Connectivity(parseResult);
        assertFalse(hardwareInfo.isIpv6Connection());
    }

    @Test
    public void setIpv6Connectivity_invalid_ping_response_should_return_no_ipv6_connectivity_2() {
        ParseResult parseResult = new ParseResult(PING_SEARCH_WORD, "invalid");
        net.setIpv6Connectivity(parseResult);
        assertFalse(hardwareInfo.isIpv6Connection());
    }

}