// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import com.yahoo.vespa.hosted.node.admin.docker.DockerNetworking;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.component.PathResolver;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

/**
 * @author dybis
 */
public class StorageMaintainerTest {
    private final Environment environment = new Environment.Builder()
            .configServerConfig(new ConfigServerConfig(new ConfigServerConfig.Builder()))
            .region("us-east-1")
            .environment("prod")
            .system("main")
            .cloud("mycloud")
            .pathResolver(new PathResolver())
            .dockerNetworking(DockerNetworking.HOST_NETWORK)
            .coredumpFeedEndpoint("http://domain.tld/docid")
            .build();
    private final DockerOperations docker = mock(DockerOperations.class);
    private final ProcessExecuter processExecuter = mock(ProcessExecuter.class);
    private final FileHelper fileHelper = mock(FileHelper.class);
    private final StorageMaintainer storageMaintainer = new StorageMaintainer(docker, processExecuter,
            environment, null, fileHelper);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testDiskUsed() throws IOException, InterruptedException {
        int writeSize = 10000;
        writeNBytesToFile(folder.newFile(), writeSize);

        long usedBytes = storageMaintainer.getDiskUsedInBytes(folder.getRoot().toPath());
        if (usedBytes * 4 < writeSize || usedBytes > writeSize * 4)
            fail("Used bytes is " + usedBytes + ", but wrote " + writeSize + " bytes, not even close.");
    }

    @Test
    public void testNonExistingDiskUsed() throws IOException, InterruptedException {
        long usedBytes = storageMaintainer.getDiskUsedInBytes(folder.getRoot().toPath().resolve("doesn't exist"));
        assertEquals(0L, usedBytes);
    }

    private static void writeNBytesToFile(File file, int nBytes) throws IOException {
        Files.write(file.toPath(), new byte[nBytes]);
    }
}
