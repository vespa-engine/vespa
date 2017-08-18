// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo.DiskType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author sgrostad
 * @author olaaun
 */

public class HardwareNodeComparatorTest {

    private HardwareInfo actualHardware;
    private HardwareInfo nodeInfo;

    @Before
    public void setup() {
        actualHardware = new HardwareInfo();
        nodeInfo = new HardwareInfo();
        actualHardware.setMinCpuCores(24);
        nodeInfo.setMinCpuCores(24);
        actualHardware.setMinMainMemoryAvailableGb(16);
        nodeInfo.setMinMainMemoryAvailableGb(16);
        nodeInfo.setInterfaceSpeedMbs(10000);
        actualHardware.setInterfaceSpeedMbs(10000);
        actualHardware.setMinDiskAvailableGb(500);
        nodeInfo.setMinDiskAvailableGb(500);
    }

    @Test
    public void compare_equal_hardware_should_create_emmpty_json() throws Exception {
        String actualJson = new ObjectMapper().writeValueAsString(HardwareNodeComparator.compare(nodeInfo, actualHardware));
        String expectedJson = "{}";
        assertEquals(expectedJson, actualJson);

    }

    @Test
    public void compare_different_amount_of_cores_should_create_json_with_actual_core_amount() throws Exception {
        actualHardware.setMinCpuCores(4);
        nodeInfo.setMinCpuCores(1);
        String actualJson = new ObjectMapper().writeValueAsString(HardwareNodeComparator.compare(nodeInfo, actualHardware));
        String expectedJson = "{\"actualcpuCores\":4}";
        assertEquals(expectedJson, actualJson);
    }

    @Test
    public void compare_different_disk_type_should_create_json_with_actual_disk_type() throws Exception {
        actualHardware.setDiskType(DiskType.SLOW);
        nodeInfo.setDiskType(DiskType.FAST);
        String actualJson = new ObjectMapper().writeValueAsString(HardwareNodeComparator.compare(nodeInfo, actualHardware));
        String expectedJson = "{\"actualDiskType\":\"SLOW\"}";
        assertEquals(expectedJson, actualJson);
    }


}
