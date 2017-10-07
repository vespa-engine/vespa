package com.yahoo.vespa.hosted.dockerapi;


import org.junit.Test;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class CreateContainerCommandImplTest {

    @Test
    public void generateMacAddressTest() {
        String[][] addresses = {
                {"test123.host.yahoo.com",  null,       "abcd:1234::1", "ee:ae:a9:de:ad:c2"},
                {"test123.host.yahoo.com",  null,       "abcd:1234::2", "fa:81:11:1b:ff:fb"},
                {"unique.host.yahoo.com",   null,       "abcd:1234::1", "96:a4:00:77:90:3b"},
                {"test123.host.yahoo.com",  "10.0.0.1", null,           "7e:de:b3:7c:9e:96"},
                {"test123.host.yahoo.com",  "10.0.0.1", "abcd:1234::1", "6a:06:af:16:25:95"}};

        Stream.of(addresses).forEach(address -> {
            String generatedMac = CreateContainerCommandImpl.generateMACAddress(
                    address[0], Optional.ofNullable(address[1]), Optional.ofNullable(address[2]));
            String expectedMac = address[3];
            assertEquals(expectedMac, generatedMac);
        });
    }
}