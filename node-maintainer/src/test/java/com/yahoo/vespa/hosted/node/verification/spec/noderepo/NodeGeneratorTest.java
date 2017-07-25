package com.yahoo.vespa.hosted.node.verification.spec.noderepo;

import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import org.junit.Test;

import java.io.File;
import java.net.URL;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * Created by olaa on 07/07/2017.
 */
public class NodeGeneratorTest {

    private static final double DELTA = 0.1;

    @Test
    public void convertJsonModel_should_return_correct_HardwareInfo() throws Exception {
        URL url = new File("src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/nodeInfoTest.json").toURI().toURL();
        NodeJsonModel nodeJsonModel = NodeInfoRetriever.retrieve(url);
        HardwareInfo hardwareInfo = NodeGenerator.convertJsonModel(nodeJsonModel);
        double expectedMinDiskAvailable = 500.0;
        double expectedMinMainMemoryAvailable = 24.0;
        double expectedMinCpuCores = 24.0;
        double expectedInterfaceSpeedMbs = 1000;
        assertEquals(expectedMinDiskAvailable, hardwareInfo.getMinDiskAvailableGb(), DELTA);
        assertEquals(expectedMinMainMemoryAvailable, hardwareInfo.getMinMainMemoryAvailableGb(), DELTA);
        assertEquals(expectedMinCpuCores, hardwareInfo.getMinCpuCores(), DELTA);
        assertTrue(hardwareInfo.getIpv4Connectivity());
        assertTrue(hardwareInfo.getIpv6Connectivity());
        assertEquals(expectedInterfaceSpeedMbs, hardwareInfo.getInterfaceSpeedMbs(), DELTA);
        assertFalse(hardwareInfo.getFastDisk());
    }

}