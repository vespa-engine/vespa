// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.spec.retrievers;


import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.VerifierSettings;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * @author sgrostad
 * @author olaaun
 */

public class HardwareInfoRetrieverTest {

    private static final String RESOURCE_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/";
    private static final String CPU_INFO_PATH = RESOURCE_PATH + "cpuinfoTest";
    private static final String MEMORY_INFO_PATH = RESOURCE_PATH + "meminfoTest";
    private static final String DISK_TYPE_INFO_PATH = RESOURCE_PATH + "DiskTypeFastDisk";
    private static final String DISK_SIZE_INFO_PATH = RESOURCE_PATH + "filesize";
    private static final String NET_INTERFACE_INFO_PATH = RESOURCE_PATH + "ifconfigNoIpv6";
    private static final String NET_INTERFACE_SPEED_INFO_PATH = RESOURCE_PATH + "eth0";
    private static String PING_RESPONSE = RESOURCE_PATH + "invalidpingresponse";
    private MockCommandExecutor mockCommandExecutor;
    private HardwareInfo expectedHardwareInfo;
    private VerifierSettings verifierSettings = spy(new VerifierSettings());
    private static final double DELTA = 0.1;

    @Before
    public void setup() {
        mockCommandExecutor = new MockCommandExecutor();
        mockCommandExecutor.addCommand("cat " + CPU_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + MEMORY_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + DISK_TYPE_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + DISK_SIZE_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + NET_INTERFACE_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + NET_INTERFACE_SPEED_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + PING_RESPONSE);

        expectedHardwareInfo = new HardwareInfo();
        expectedHardwareInfo.setMinCpuCores(4);
        expectedHardwareInfo.setMinMainMemoryAvailableGb(4.042128);
        expectedHardwareInfo.setInterfaceSpeedMbs(1000);
        expectedHardwareInfo.setMinDiskAvailableGb(1760.0);
        expectedHardwareInfo.setIpv4Interface(true);
        expectedHardwareInfo.setIpv6Interface(false);
        expectedHardwareInfo.setIpv6Connection(false);
        expectedHardwareInfo.setDiskType(HardwareInfo.DiskType.FAST);
    }

    @Test
    public void retriever_should_return_valid_HardwareInfo() {
        doReturn(true).when(verifierSettings).isCheckIPv6();
        HardwareInfo actualHardwareInfo = HardwareInfoRetriever.retrieve(mockCommandExecutor, verifierSettings);
        assertEquals(expectedHardwareInfo.getMinDiskAvailableGb(), actualHardwareInfo.getMinDiskAvailableGb(), DELTA);
        assertEquals(expectedHardwareInfo.getMinMainMemoryAvailableGb(), actualHardwareInfo.getMinMainMemoryAvailableGb(), DELTA);
        assertEquals(expectedHardwareInfo.getMinCpuCores(), actualHardwareInfo.getMinCpuCores());
        assertEquals(expectedHardwareInfo.getIpv4Interface(), actualHardwareInfo.getIpv4Interface());
        assertEquals(expectedHardwareInfo.getIpv6Interface(), actualHardwareInfo.getIpv6Interface());
        assertEquals(expectedHardwareInfo.getInterfaceSpeedMbs(), actualHardwareInfo.getInterfaceSpeedMbs(), DELTA);
        assertEquals(expectedHardwareInfo.getDiskType(), actualHardwareInfo.getDiskType());
        assertEquals(expectedHardwareInfo.isIpv6Connection(), actualHardwareInfo.isIpv6Connection());
    }
}
