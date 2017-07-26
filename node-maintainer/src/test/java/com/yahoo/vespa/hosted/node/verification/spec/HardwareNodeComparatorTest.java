package com.yahoo.vespa.hosted.node.verification.spec;

import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo.DiskType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by olaa on 07/07/2017.
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
    public void compare_should_be_equal() {
        assertTrue(HardwareNodeComparator.compare(nodeInfo, actualHardware).getMetrics().isMatch());

    }

    @Test
    public void compare_different_amount_of_cores_should_be_false() {
        actualHardware.setMinCpuCores(4);
        nodeInfo.setMinCpuCores(1);
        assertFalse(HardwareNodeComparator.compare(nodeInfo, actualHardware).getMetrics().isMatch());
    }

    @Test
    public void compare_different_disk_type_should_return_false() {
        actualHardware.setDiskType(DiskType.UNKNOWN);
        nodeInfo.setDiskType(DiskType.FAST);
        assertFalse(HardwareNodeComparator.compare(nodeInfo, actualHardware).getMetrics().isMatch());
    }



}