package com.yahoo.vespa.hosted.node.verification.spec.noderepo;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class IPAddressVerifierTest {

    IPAddressVerifier ipAddressVerifier = spy(new IPAddressVerifier());
    String ipAddress;
    String additionalIp1;
    String additionalIp2;
    String additionalIp3;
    String[] additionalIpAddresses;

    @Before
    public void setup() {
        ipAddress = "2001:4998:c:2977::1060";
        additionalIp1 = "2001:4998:c:2977::106f";
        additionalIp2 = "2001:4998:c:2977::106a";
        additionalIp3 = "2001:4998:c:2977::106c";
        additionalIpAddresses = new String[] {additionalIp1, additionalIp2, additionalIp3};
    }
    @Test
    public void test_verifyAdditionalIpAddress_should_add_IP_address_when_different_hostname() throws Exception{
        String realHostName = "www.yahoo.com";
        String wrongHostName = "www.nrk.no";
        doReturn(realHostName).when(ipAddressVerifier).reverseLookUp(ipAddress);
        doReturn(realHostName).when(ipAddressVerifier).reverseLookUp(additionalIp1);
        doReturn(realHostName).when(ipAddressVerifier).reverseLookUp(additionalIp2);
        doReturn(wrongHostName).when(ipAddressVerifier).reverseLookUp(additionalIp3);
        String[] faultyIpAddresses = ipAddressVerifier.getFaultyIpAddresses(ipAddress, additionalIpAddresses);
        String[] expectedFaultyIpAddresses = new String[] {additionalIp3};
        assertEquals(expectedFaultyIpAddresses, faultyIpAddresses);
    }

    @Test
    public void test_convertToLookupFormat_should_return_properly_converted_ipv6_address() {
        String ipv6Address = "2001:db8::567:89ab";
        String actualConvertedAddress = ipAddressVerifier.convertToLookupFormat(ipv6Address);
        String expectedConvertedAddress = "b.a.9.8.7.6.5.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa";
        assertEquals(expectedConvertedAddress, actualConvertedAddress);
    }

}