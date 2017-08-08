package com.yahoo.vespa.hosted.node.verification.commons.noderepo;

import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by olaa on 07/07/2017.
 */
public class NodeJsonConverterTest {

    private static final double DELTA = 0.1;

    @Test
    public void convertJsonModel_should_return_correct_HardwareInfo() throws Exception {
        ArrayList<URL> urls = new ArrayList<>(Arrays.asList(new File("src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/nodeInfoTest.json").toURI().toURL()));
        NodeRepoJsonModel nodeRepoJsonModel = NodeRepoInfoRetriever.retrieve(urls);
        HardwareInfo hardwareInfo = NodeJsonConverter.convertJsonModelToHardwareInfo(nodeRepoJsonModel);
        double expectedMinDiskAvailable = 500.0;
        double expectedMinMainMemoryAvailable = 24.0;
        double expectedMinCpuCores = 24.0;
        double expectedInterfaceSpeedMbs = 1000;
        assertEquals(expectedMinDiskAvailable, hardwareInfo.getMinDiskAvailableGb(), DELTA);
        assertEquals(expectedMinMainMemoryAvailable, hardwareInfo.getMinMainMemoryAvailableGb(), DELTA);
        assertEquals(expectedMinCpuCores, hardwareInfo.getMinCpuCores(), DELTA);
        assertTrue(hardwareInfo.getIpv4Interface());
        assertFalse(hardwareInfo.getIpv6Interface());
        assertEquals(expectedInterfaceSpeedMbs, hardwareInfo.getInterfaceSpeedMbs(), DELTA);
        assertEquals(hardwareInfo.getDiskType(), HardwareInfo.DiskType.SLOW);
    }

}