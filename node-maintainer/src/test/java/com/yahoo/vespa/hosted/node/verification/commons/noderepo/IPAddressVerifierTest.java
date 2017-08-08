package com.yahoo.vespa.hosted.node.verification.commons.noderepo;

import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class IPAddressVerifierTest {

    private IPAddressVerifier ipAddressVerifier = spy(new IPAddressVerifier());
    private String ipv4Address;
    private String ipv6Address;
    private NodeRepoJsonModel nodeRepoJsonModel;
    private static final String ABSOLUTE_PATH = Paths.get(".").toAbsolutePath().normalize().toString();
    private static final String RESOURCE_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/IPAddressVerifierTest.json";
    private static final String URL_RESOURCE_PATH = "file://" + ABSOLUTE_PATH + "/" + RESOURCE_PATH;
    private String ipv4LookupFormat;
    private String ipv6LookupFormat;

    @Before
    public void setup() throws Exception {
        ipv4Address = "10.213.181.113";
        ipv6Address = "2001:4998:c:2940:0:0:0:111c";
        ArrayList<URL> nodeRepoUrl = new ArrayList<>(Arrays.asList(new URL(URL_RESOURCE_PATH)));
        nodeRepoJsonModel = NodeRepoInfoRetriever.retrieve(nodeRepoUrl);
        ipv4LookupFormat = "113.181.213.10.in-addr.arpa";
        ipv6LookupFormat = "c.1.1.1.0.0.0.0.0.0.0.0.0.0.0.0.0.4.9.2.c.0.0.0.8.9.9.4.1.0.0.2.ip6.arpa";
    }

    @Test
    public void getFaultyIpAddresses_should_return_IP_address_when_different_hostname() throws Exception {
        String realHostName = "host.name";
        String wrongHostName = "www.yahoo.com";
        doReturn(realHostName).when(ipAddressVerifier).reverseLookUp(ipv4LookupFormat);
        doReturn(wrongHostName).when(ipAddressVerifier).reverseLookUp(ipv6LookupFormat);
        String[] faultyIpAddresses = ipAddressVerifier.getFaultyIpAddresses(nodeRepoJsonModel);
        String[] expectedFaultyIpAddresses = new String[]{ipv6Address};
        assertArrayEquals(expectedFaultyIpAddresses, faultyIpAddresses);
    }

    @Test
    public void getFaultyIpAddresses_should_return_empty_array_when_all_addresses_point_to_correct_hostname() throws Exception {
        String realHostName = "host.name";
        doReturn(realHostName).when(ipAddressVerifier).reverseLookUp(ipv4LookupFormat);
        doReturn(realHostName).when(ipAddressVerifier).reverseLookUp(ipv6LookupFormat);
        String[] faultyIpAddresses = ipAddressVerifier.getFaultyIpAddresses(nodeRepoJsonModel);
        String[] expectedFaultyIpAddresses = new String[]{};
        assertArrayEquals(expectedFaultyIpAddresses, faultyIpAddresses);
    }

    @Test
    public void convertIpv6ToLookupFormat_should_return_properly_converted_ipv6_address() {
        String actualConvertedAddress = ipAddressVerifier.convertIpv6ToLookupFormat(ipv6Address);
        assertEquals(ipv6LookupFormat, actualConvertedAddress);
    }

    @Test
    public void convertIpv4ToLookupFormat_should_return_properly_converted_ipv6_address() {
        String actualConvertedAddress = ipAddressVerifier.convertIpv4ToLookupFormat(ipv4Address);
        assertEquals(ipv4LookupFormat, actualConvertedAddress);
    }

    @Test
    public void getFaultyIpAddresses_should_return_empty_array_when_parameters_are_invalid() {
        assertEquals(0, ipAddressVerifier.getFaultyIpAddresses(new NodeRepoJsonModel()).length);
    }

}