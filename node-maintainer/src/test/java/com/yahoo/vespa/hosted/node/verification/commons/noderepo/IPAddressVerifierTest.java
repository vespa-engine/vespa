// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons.noderepo;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * @author sgrostad
 * @author olaaun
 */
public class IPAddressVerifierTest {

    private final String ipv4Address = "10.2.4.8";
    private final String ipv6Address = "fdab:0:0:0:0:0:0:1234";
    private final NodeSpec nodeSpec = new NodeSpec(1920, 256, 48, true, new String[]{ipv4Address, ipv6Address});
    private final String hostname = "test123.region.domain.tld";

    private IPAddressVerifier ipAddressVerifier = spy(new IPAddressVerifier(hostname));
    private String ipv4LookupFormat;
    private String ipv6LookupFormat;

    @Before
    public void setup() throws Exception {
        ipv4LookupFormat = "8.4.2.10.in-addr.arpa";
        ipv6LookupFormat = "4.3.2.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.b.a.d.f.ip6.arpa";
    }

    @Test
    public void getFaultyIpAddresses_should_return_IP_address_when_different_hostname() throws Exception {
        String wrongHostName = "www.yahoo.com";
        doReturn(hostname).when(ipAddressVerifier).reverseLookUp(ipv4LookupFormat);
        doReturn(wrongHostName).when(ipAddressVerifier).reverseLookUp(ipv6LookupFormat);
        String[] faultyIpAddresses = ipAddressVerifier.getFaultyIpAddresses(nodeSpec);
        String[] expectedFaultyIpAddresses = new String[]{ipv6Address};
        assertArrayEquals(expectedFaultyIpAddresses, faultyIpAddresses);
    }

    @Test
    public void getFaultyIpAddresses_should_return_empty_array_when_all_addresses_point_to_correct_hostname() throws Exception {
        doReturn(hostname).when(ipAddressVerifier).reverseLookUp(ipv4LookupFormat);
        doReturn(hostname).when(ipAddressVerifier).reverseLookUp(ipv6LookupFormat);
        String[] faultyIpAddresses = ipAddressVerifier.getFaultyIpAddresses(nodeSpec);
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
        final NodeSpec nodeWithNoIP = new NodeSpec(1920, 256, 48, true, new String[0]);
        assertEquals(0, ipAddressVerifier.getFaultyIpAddresses(nodeWithNoIP).length);
    }

}
