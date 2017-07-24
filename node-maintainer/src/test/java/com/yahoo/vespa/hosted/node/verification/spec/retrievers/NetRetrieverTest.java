package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.spec.parse.ParseResult;
import com.yahoo.vespa.hosted.node.verification.spec.mock.MockCommandExecutor;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Created by sgrostad on 07/07/2017.
 */
public class NetRetrieverTest {

    private final String NET_FIND_INTERFACE = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/ifconfig";
    private final String NET_CHECK_INTERFACE_SPEED = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/";
    private HardwareInfo hardwareInfo;
    private MockCommandExecutor commandExecutor;
    private NetRetriever net;
    private ArrayList<ParseResult> parseResults;

    @Before
    public void setup(){
        hardwareInfo = new HardwareInfo();
        commandExecutor = new MockCommandExecutor();
        net = new NetRetriever(hardwareInfo, commandExecutor);
        parseResults = new ArrayList<>();
    }

    @Test
    public void test_updateInfo_should_store_ipv4_ipv6_connectivity_and_interface_speed() {
        commandExecutor.addCommand("cat " + NET_FIND_INTERFACE);
        commandExecutor.addCommand("cat " + NET_CHECK_INTERFACE_SPEED + "eth0");
        net.updateInfo();
        assertTrue(hardwareInfo.getIpv4Connectivity());
        assertTrue(hardwareInfo.getIpv6Connectivity());
        double expectedInterfaceSpeed = 1000;
        double delta = 0.01;
        assertEquals(expectedInterfaceSpeed, hardwareInfo.getInterfaceSpeedMbs(), delta);
    }

    @Test
    public void test_findInterface_valid_input() throws IOException{
        commandExecutor.addCommand("cat " + NET_FIND_INTERFACE);
        parseResults = net.findInterface();
        ParseResult expectedParseResult = new ParseResult("eth0", "eth0");
        assertEquals(expectedParseResult, parseResults.get(0));
    }

    @Test
    public void test_findInterfaceSpeed_valid_input() throws IOException{
        commandExecutor.addCommand("cat " + NET_FIND_INTERFACE);
        commandExecutor.addCommand("cat " + NET_CHECK_INTERFACE_SPEED + "eth0");
        parseResults = net.findInterface();
        net.findInterfaceSpeed(parseResults);
        ParseResult expectedParseResults = new ParseResult("Speed", "1000Mb/s");
        assertEquals(expectedParseResults, parseResults.get(3));
    }

    @Test
    public void test_parseNetInterface_get_ipv_from_ifconfig_testFile() throws IOException {
        ArrayList<String> mockOutput = commandExecutor.readFromFile(NET_FIND_INTERFACE);
        parseResults = net.parseNetInterface(mockOutput);
        net.updateHardwareInfoWithNet(parseResults);
        assertTrue(hardwareInfo.getIpv4Connectivity());
        assertTrue(hardwareInfo.getIpv6Connectivity());
    }

    @Test
    public void test_parseNetInterface_get_ipv_from_ifconfigNotIpv6_testFile() throws IOException {
        ArrayList<String> mockOutput = commandExecutor.readFromFile(NET_FIND_INTERFACE+ "NoIpv6");
        parseResults = net.parseNetInterface(mockOutput);

        ArrayList<ParseResult> expextedParseResults = new ArrayList<>(Arrays.asList(
                new ParseResult("eth0","eth0"),
                new ParseResult("inet","inet")));
        assertEquals(expextedParseResults, parseResults);
    }

    @Test
    public void test_parseNetInterface_get_interfaceName_from_ifconfig_testFile() throws IOException{
        ArrayList<String> mockOutput = commandExecutor.readFromFile(NET_FIND_INTERFACE);
        parseResults = net.parseNetInterface(mockOutput);
        String interfaceName = net.findInterfaceName(parseResults);
        String expectedInterfaceName = "eth0";
        assertEquals(expectedInterfaceName, interfaceName);
    }

    @Test
    public void test_parseInterfaceSpeed_get_interfaceSpeed_from_eth0_testFile() throws IOException{
        ArrayList<String> mockOutput = commandExecutor.readFromFile("src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/eth0");
        ParseResult parseResult = net.parseInterfaceSpeed(mockOutput);
        ParseResult expectedParseResult = new ParseResult("Speed","1000Mb/s");
        assertEquals(expectedParseResult,parseResult);
    }

    @Test
    public void test_findInterfaceName_should_return_interface_name(){
        parseResults.add(new ParseResult("eth0", "eth0"));
        String expectedInterfaceName = "eth0";
        assertEquals(expectedInterfaceName, net.findInterfaceName(parseResults));
    }
    @Test
    public void test_findInterfaceName_should_return_empty_interface_name(){
        parseResults.add(new ParseResult("et", "et0"));
        String expectedInterfaceName = "";
        assertEquals(expectedInterfaceName, net.findInterfaceName(parseResults));
    }

    @Test
    public void test_updateHardwareinfoWithNet_valid_input(){
        parseResults.add(new ParseResult("eth0", "eth0"));
        parseResults.add(new ParseResult("inet", "inet"));
        parseResults.add(new ParseResult("inet6", "inet6"));
        parseResults.add(new ParseResult("Speed", "1000Mb/s"));
        net.updateHardwareInfoWithNet(parseResults);
        double expectedInterfaceSpeed = 1000;
        double delta = 0.01;
        assertEquals(expectedInterfaceSpeed, hardwareInfo.getInterfaceSpeedMbs(), delta);
        assertTrue(hardwareInfo.getIpv4Connectivity());
        assertTrue(hardwareInfo.getIpv6Connectivity());
    }
}