package com.yahoo.vespa.hosted.node.admin.containerdata;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.node.admin.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MotdContainerDataTest {

    @Test
    public void writesMotd() {
        MotdContainerData motdContainerData = new MotdContainerData(
                new NodeSpec.Builder()
                        .nodeType(NodeType.tenant)
                        .owner(new NodeSpec.Owner("tenant1", "application1", "default"))
                        .nodeState(Node.State.dirty)
                        .vespaVersion("7.0.0")
                        .hostname("nope")
                        .nodeFlavor("D-WAVE")
                        .allowedToBeDown(false)
                        .membership(new NodeSpec.Membership(null, null, null, 0, false))
                        .minCpuCores(0)
                        .minMainMemoryAvailableGb(0)
                        .minDiskAvailableGb(0)
                        .fastDisk(false)
                        .ipAddresses(Collections.emptySet())
                        .build(),
                new Environment.Builder()
                        .configServerConfig(new ConfigServerConfig(new ConfigServerConfig.Builder()))
                        .system("main")
                        .environment("prod")
                        .region("aws-us-east-1a")
                        .defaultFlavor("cherry")
                        .cloud("mycloud")
                        .build());

        motdContainerData.writeTo((path, content) -> {
            assertEquals(path, Paths.get("etc/profile.d/motd.sh"));

            System.out.println(content);

            assertTrue(content.contains("tenant"));
            assertTrue(content.contains("D-WAVE"));
            assertTrue(content.contains("[0;91m"));
            assertTrue(content.contains("MAIN PROD AWS-US-EAST-1A"));
            assertTrue(content.contains("tenant1:application1:default"));
            assertTrue(content.contains("dirty"));
            assertTrue(content.contains("wanted = unknown"));
            assertTrue(content.contains("installed = 7.0.0"));
        });

    }

}
