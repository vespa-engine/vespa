// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeRepoInfoRetriever;
import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeSpec;
import com.yahoo.vespa.hosted.node.verification.commons.report.SpecVerificationReport;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author sgrostad
 * @author olaaun
 */

public class SpecVerifierTest {

    private MockCommandExecutor mockCommandExecutor;
    private static final String ABSOLUTE_PATH = Paths.get(".").toAbsolutePath().normalize().toString();
    private static final String RESOURCE_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources";
    private static final String URL_RESOURCE_PATH = "file://" + ABSOLUTE_PATH + "/" + RESOURCE_PATH;
    private static final String NODE_REPO_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/nodeInfoTest.json";
    private static final String CPU_INFO_PATH = RESOURCE_PATH + "/cpuinfoTest";
    private static final String MEMORY_INFO_PATH = RESOURCE_PATH + "/meminfoTest";
    private static final String FAST_DISK_TYPE_INFO_PATH = RESOURCE_PATH + "/DiskTypeFastDisk";
    private static final String NON_FAST_DISK_TYPE_INFO_PATH = RESOURCE_PATH + "/DiskTypeNonFastDisk";
    private static final String DISK_SIZE_INFO_PATH = RESOURCE_PATH + "/filesize";
    private static final String NET_INTERFACE_INFO_PATH = RESOURCE_PATH + "/ifconfig";
    private static final String NET_INTERFACE_SPEED_INFO_PATH = RESOURCE_PATH + "/eth0";
    private static final String PING_RESPONSE = RESOURCE_PATH + "/validpingresponse";
    private static final String INVALID_PING_RESPONSE = RESOURCE_PATH + "/pingresponse-all-packets-lost";
    private static final double DELTA = 0.1;
    List<URL> nodeInfoUrls;

    @Before
    public void setup() {
        mockCommandExecutor = new MockCommandExecutor();
        nodeInfoUrls = new ArrayList<>();

    }


    @Test
    public void verifySpec_equal_nodeRepoInfo_and_hardware_should_return_true() throws Exception {
        nodeInfoUrls.add(new URL(URL_RESOURCE_PATH + "/nodeRepo.json"));
        mockCommandExecutor.addCommand("cat " + CPU_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + MEMORY_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + FAST_DISK_TYPE_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + DISK_SIZE_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + NET_INTERFACE_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + NET_INTERFACE_SPEED_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + PING_RESPONSE);
        assertTrue(SpecVerifier.verifySpec(mockCommandExecutor, nodeInfoUrls));
    }

    @Test
    public void verifySpec_unequal_nodeRepoInfo_and_hardware_should_return_false() throws Exception {
        nodeInfoUrls.add(new URL(URL_RESOURCE_PATH + "/nodeRepo.json"));
        mockCommandExecutor.addCommand("cat " + CPU_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + MEMORY_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + NON_FAST_DISK_TYPE_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + DISK_SIZE_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + NET_INTERFACE_INFO_PATH + "NoIpv6");
        mockCommandExecutor.addCommand("cat " + NET_INTERFACE_SPEED_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + INVALID_PING_RESPONSE);
        assertFalse(SpecVerifier.verifySpec(mockCommandExecutor, nodeInfoUrls));
    }

    @Test
    public void makeVerificationSpecReport_should_return_false_interface_speed_and_ipv6_connection() throws Exception {
        HardwareInfo actualHardware = new HardwareInfo();
        actualHardware.setMinCpuCores(24);
        actualHardware.setMinMainMemoryAvailableGb(24);
        actualHardware.setInterfaceSpeedMbs(100); //this is wrong
        actualHardware.setMinDiskAvailableGb(500);
        actualHardware.setIpv4Interface(true);
        actualHardware.setIpv6Interface(true);
        actualHardware.setIpv6Connection(true);
        actualHardware.setDiskType(HardwareInfo.DiskType.SLOW);
        nodeInfoUrls.add(new File(NODE_REPO_PATH).toURI().toURL());
        NodeSpec nodeSpec = NodeRepoInfoRetriever.retrieve(nodeInfoUrls);
        SpecVerificationReport verificationSpecVerificationReport = SpecVerifier.makeVerificationReport(actualHardware, nodeSpec);
        String expectedJson = "{\"actualInterfaceSpeed\":100.0}";
        ObjectMapper om = new ObjectMapper();
        String actualJson = om.writeValueAsString(verificationSpecVerificationReport);
        assertEquals(expectedJson, actualJson);
    }

    @Test
    public void getNodeRepositoryJSON_should_return_valid_nodeRepoJSONModel() throws Exception {
        nodeInfoUrls.add(new URL(URL_RESOURCE_PATH + "/nodeRepo.json"));
        NodeSpec actualNodeSpec = SpecVerifier.getNodeRepositoryJSON(nodeInfoUrls);
        double expectedMinCpuCores = 4D;
        double expectedMinMainMemoryAvailableGb = 4.04D;
        double expectedMinDiskAvailableGb = 1759.84;
        boolean expectedFastDisk = true;
        assertEquals(expectedMinCpuCores, actualNodeSpec.getMinCpuCores(), DELTA);
        assertEquals(expectedMinMainMemoryAvailableGb, actualNodeSpec.getMinMainMemoryAvailableGb(), DELTA);
        assertEquals(expectedMinDiskAvailableGb, actualNodeSpec.getMinDiskAvailableGb(), DELTA);
        assertEquals(expectedFastDisk, actualNodeSpec.isFastDisk());
    }

}
