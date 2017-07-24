package com.yahoo.vespa.hosted.node.verification.spec.noderepo;

import com.yahoo.vespa.hosted.node.verification.spec.hardware.HardwareInfo;
import org.junit.Test;


import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.*;

/**
 * Created by sgrostad on 06/07/2017.
 */
public class NodeExecuteCommandTest {

    @Test
    public void test_retrieve_should_return_correctly_mapped_NodeJsonModel() throws MalformedURLException{
        URL url = new File("src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/nodeInfoTest.json").toURI().toURL();
        HardwareInfo node = NodeInfoRetriever.retrieve(url);
        double expectedMinDiskAvailable = 500.0;
        double expectedMinMainMemoryAvailable = 24.0;
        double expectedMinCpuCores = 24.0;
        double delta = 0.1;
        assertEquals(expectedMinDiskAvailable,node.getMinDiskAvailableGb(),delta);
        assertEquals(expectedMinMainMemoryAvailable,node.getMinMainMemoryAvailableGb(),delta);
        assertEquals(expectedMinCpuCores,node.getMinCpuCores(),delta);
    }

}