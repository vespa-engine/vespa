// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.google.common.collect.ImmutableSet;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import com.yahoo.vespa.hosted.node.admin.docker.DockerNetworking;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.component.PathResolver;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextImplTest;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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
            .build();
    private final DockerOperations docker = mock(DockerOperations.class);
    private final ProcessExecuter processExecuter = mock(ProcessExecuter.class);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testDiskUsed() throws IOException, InterruptedException {
        StorageMaintainer storageMaintainer = new StorageMaintainer(docker, processExecuter, environment, null, null);
        int writeSize = 10000;
        Files.write(folder.newFile().toPath(), new byte[writeSize]);

        long usedBytes = storageMaintainer.getDiskUsedInBytes(folder.getRoot().toPath());
        if (usedBytes * 4 < writeSize || usedBytes > writeSize * 4)
            fail("Used bytes is " + usedBytes + ", but wrote " + writeSize + " bytes, not even close.");
    }

    @Test
    public void testNonExistingDiskUsed() throws IOException, InterruptedException {
        StorageMaintainer storageMaintainer = new StorageMaintainer(docker, processExecuter, environment, null, null);
        long usedBytes = storageMaintainer.getDiskUsedInBytes(folder.getRoot().toPath().resolve("doesn't exist"));
        assertEquals(0L, usedBytes);
    }

    @Test
    public void archive_container_data_test() throws IOException {
        // Create some files in containers
        FileSystem fileSystem = TestFileSystem.create();
        NodeAgentContext context1 = createNodeAgentContextAndContainerStorage(fileSystem, "container-1");
        createNodeAgentContextAndContainerStorage(fileSystem, "container-2");

        Path pathToArchiveDir = fileSystem.getPath("/home/docker/container-archive");
        Files.createDirectories(pathToArchiveDir);

        Path containerStorageRoot = context1.pathOnHostFromPathInNode("/").getParent();
        Set<String> containerStorageRootContentsBeforeArchive = FileFinder.from(containerStorageRoot)
                .maxDepth(1)
                .stream()
                .map(FileFinder.FileAttributes::filename)
                .collect(Collectors.toSet());
        assertEquals(ImmutableSet.of("container-archive", "container-1", "container-2"), containerStorageRootContentsBeforeArchive);


        // Archive container-1
        StorageMaintainer storageMaintainer = new StorageMaintainer(docker, processExecuter, environment, null, pathToArchiveDir);
        storageMaintainer.archiveNodeStorage(context1);

        // container-1 should be gone from container-storage
        Set<String> containerStorageRootContentsAfterArchive = FileFinder.from(containerStorageRoot)
                .maxDepth(1)
                .stream()
                .map(FileFinder.FileAttributes::filename)
                .collect(Collectors.toSet());
        assertEquals(ImmutableSet.of("container-archive", "container-2"), containerStorageRootContentsAfterArchive);

        // container archive directory should contain exactly 1 directory - the one we just archived
        List<FileFinder.FileAttributes> containerArchiveContentsAfterArchive = FileFinder.from(pathToArchiveDir).maxDepth(1).list();
        assertEquals(1, containerArchiveContentsAfterArchive.size());
        Path archivedContainerStoragePath = containerArchiveContentsAfterArchive.get(0).path();
        assertTrue(archivedContainerStoragePath.getFileName().toString().matches("container-1_[0-9]{14}"));
        Set<String> archivedContainerStorageContents = FileFinder.files(archivedContainerStoragePath)
                .stream()
                .map(fileAttributes -> archivedContainerStoragePath.relativize(fileAttributes.path()).toString())
                .collect(Collectors.toSet());
        assertEquals(ImmutableSet.of("opt/vespa/logs/vespa/vespa.log", "opt/vespa/logs/vespa/zookeeper.log"), archivedContainerStorageContents);
    }

    private NodeAgentContext createNodeAgentContextAndContainerStorage(FileSystem fileSystem, String containerName) throws IOException {
        NodeAgentContext context = NodeAgentContextImplTest.nodeAgentFromHostname(fileSystem, containerName + ".domain.tld");

        Path containerVespaHomeOnHost = context.pathOnHostFromPathInNode(context.pathInNodeUnderVespaHome(""));
        Files.createDirectories(context.pathOnHostFromPathInNode("/etc/something"));
        Files.createFile(context.pathOnHostFromPathInNode("/etc/something/conf"));

        Files.createDirectories(containerVespaHomeOnHost.resolve("logs/vespa"));
        Files.createFile(containerVespaHomeOnHost.resolve("logs/vespa/vespa.log"));
        Files.createFile(containerVespaHomeOnHost.resolve("logs/vespa/zookeeper.log"));

        Files.createDirectories(containerVespaHomeOnHost.resolve("var/db"));
        Files.createFile(containerVespaHomeOnHost.resolve("var/db/some-file"));

        Path containerRootOnHost = context.pathOnHostFromPathInNode("/");
        Set<String> actualContents = FileFinder.files(containerRootOnHost)
                .stream()
                .map(fileAttributes -> containerRootOnHost.relativize(fileAttributes.path()).toString())
                .collect(Collectors.toSet());
        Set<String> expectedContents = new HashSet<>(Arrays.asList(
                "etc/something/conf",
                "opt/vespa/logs/vespa/vespa.log",
                "opt/vespa/logs/vespa/zookeeper.log",
                "opt/vespa/var/db/some-file"));
        assertEquals(expectedContents, actualContents);
        return context;
    }
}
