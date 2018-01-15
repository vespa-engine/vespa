// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons.noderepo;

import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author sgrostad
 * @author olaaun
 */

public class NodeJsonConverterTest {

    private static final double DELTA = 0.1;

    @Test
    public void convertJsonModel_should_return_correct_HardwareInfo() throws Exception {
        List<URL> urls = new ArrayList<>(Arrays.asList(new File("src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/nodeInfoTest.json").toURI().toURL()));
        NodeSpec nodeSpec = NodeRepoInfoRetriever.retrieve(urls);
        HardwareInfo hardwareInfo = NodeJsonConverter.convertJsonModelToHardwareInfo(nodeSpec);
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
