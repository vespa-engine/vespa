package com.yahoo.vespa.hosted.node.admin.containerdata;

import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PromptContainerDataTest {

    @Test
    public void writesMotd() {
        PromptContainerData promptContainerData = new PromptContainerData(new Environment.Builder()
                                                                                  .configServerConfig(new ConfigServerConfig(new ConfigServerConfig.Builder()))
                                                                                  .system("main")
                                                                                  .environment("prod")
                                                                                  .region("aws-us-east-1a")
                                                                                  .build());

        promptContainerData.writeTo((path, content) -> {
            assertEquals(path, Paths.get("etc/profile.d/prompt.sh"));

            assertTrue(content.contains("[0;91m"));
            assertTrue(content.contains("AWS-US-EAST-1A"));
        });

    }

}
