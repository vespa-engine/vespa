// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.maintenance.coredump.CoredumpHandler;
import com.yahoo.vespa.hosted.node.admin.maintenance.disk.DiskCleanup;
import com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncClient;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextImpl;
import com.yahoo.vespa.hosted.node.admin.task.util.file.DiskSize;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;
import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerPath;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestTerminal;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @author dybis
 */
public class StorageMaintainerTest {

    private final TestTerminal terminal = new TestTerminal();
    private final CoredumpHandler coredumpHandler = mock(CoredumpHandler.class);
    private final DiskCleanup diskCleanup = mock(DiskCleanup.class);
    private final SyncClient syncClient = mock(SyncClient.class);
    private final ManualClock clock = new ManualClock(Instant.ofEpochSecond(1234567890));
    private final FileSystem fileSystem = TestFileSystem.create();
    private final StorageMaintainer storageMaintainer = new StorageMaintainer(terminal, coredumpHandler, diskCleanup, syncClient, clock,
            fileSystem.getPath("/data/vespa/storage/container-archive"));

    @Test
    void testDiskUsed() {
        NodeAgentContext context = NodeAgentContextImpl.builder("host-1.domain.tld").fileSystem(fileSystem).build();

        terminal.expectCommand("du -xsk /data/vespa/storage/host-1 2>&1", 0, "321\t/data/vespa/storage/host-1/");
        assertEquals(Optional.of(DiskSize.of(328_704)), storageMaintainer.diskUsageFor(context));

        // Value should still be cached, no new execution against the terminal
        assertEquals(Optional.of(DiskSize.of(328_704)), storageMaintainer.diskUsageFor(context));
    }

    @Test
    void testNonExistingDiskUsed() {
        DiskSize size = storageMaintainer.getDiskUsed(null, Paths.get("/fake/path"));
        assertEquals(DiskSize.ZERO, size);
    }

    @Test
    void archive_container_data_test() throws IOException {
        // Create some files in containers
        NodeAgentContext context1 = createNodeAgentContextAndContainerStorage(fileSystem, "container-1");
        createNodeAgentContextAndContainerStorage(fileSystem, "container-2");

        Path pathToArchiveDir = fileSystem.getPath("/data/vespa/storage/container-archive");
        Files.createDirectories(pathToArchiveDir);

        Path containerStorageRoot = context1.paths().of("/").pathOnHost().getParent();
        Set<String> containerStorageRootContentsBeforeArchive = FileFinder.from(containerStorageRoot)
                .maxDepth(1)
                .stream()
                .map(FileFinder.FileAttributes::filename)
                .collect(Collectors.toSet());
        assertEquals(Set.of("container-archive", "container-1", "container-2"), containerStorageRootContentsBeforeArchive);


        // Archive container-1
        storageMaintainer.archiveNodeStorage(context1);

        clock.advance(Duration.ofSeconds(3));
        storageMaintainer.archiveNodeStorage(context1);

        // container-1 should be gone from container-storage
        Set<String> containerStorageRootContentsAfterArchive = FileFinder.from(containerStorageRoot)
                .maxDepth(1)
                .stream()
                .map(FileFinder.FileAttributes::filename)
                .collect(Collectors.toSet());
        assertEquals(Set.of("container-archive", "container-2"), containerStorageRootContentsAfterArchive);

        // container archive directory should contain exactly 1 directory - the one we just archived
        List<FileFinder.FileAttributes> containerArchiveContentsAfterArchive = FileFinder.from(pathToArchiveDir).maxDepth(1).list();
        assertEquals(1, containerArchiveContentsAfterArchive.size());
        Path archivedContainerStoragePath = containerArchiveContentsAfterArchive.get(0).path();
        assertEquals("container-1_20090213233130", archivedContainerStoragePath.getFileName().toString());
        Set<String> archivedContainerStorageContents = FileFinder.files(archivedContainerStoragePath)
                .stream()
                .map(fileAttributes -> archivedContainerStoragePath.relativize(fileAttributes.path()).toString())
                .collect(Collectors.toSet());
        assertEquals(Set.of("opt/vespa/logs/vespa/vespa.log", "opt/vespa/logs/vespa/zookeeper.log"), archivedContainerStorageContents);
    }

    private static NodeAgentContext createNodeAgentContextAndContainerStorage(FileSystem fileSystem, String containerName) throws IOException {
        NodeAgentContext context = NodeAgentContextImpl.builder(containerName + ".domain.tld")
                .fileSystem(fileSystem).build();

        ContainerPath containerVespaHome = context.paths().underVespaHome("");
        Files.createDirectories(context.paths().of("/etc/something"));
        Files.createFile(context.paths().of("/etc/something/conf"));

        Files.createDirectories(containerVespaHome.resolve("logs/vespa"));
        Files.createFile(containerVespaHome.resolve("logs/vespa/vespa.log"));
        Files.createFile(containerVespaHome.resolve("logs/vespa/zookeeper.log"));

        Files.createDirectories(containerVespaHome.resolve("var/db"));
        Files.createFile(containerVespaHome.resolve("var/db/some-file"));

        ContainerPath containerRoot = context.paths().of("/");
        Set<String> actualContents = FileFinder.files(containerRoot)
                .stream()
                .map(fileAttributes -> containerRoot.relativize(fileAttributes.path()).toString())
                .collect(Collectors.toSet());
        Set<String> expectedContents = Set.of(
                "etc/something/conf",
                "opt/vespa/logs/vespa/vespa.log",
                "opt/vespa/logs/vespa/zookeeper.log",
                "opt/vespa/var/db/some-file");
        assertEquals(expectedContents, actualContents);
        return context;
    }

    @Test
    void not_run_if_not_enough_used() {
        NodeAgentContext context = NodeAgentContextImpl.builder(
                NodeSpec.Builder.testSpec("h123a.domain.tld").realResources(new NodeResources(1, 1, 1, 1)).build())
                .fileSystem(fileSystem).build();
        mockDiskUsage(500L);

        storageMaintainer.cleanDiskIfFull(context);
        verifyNoInteractions(diskCleanup);
    }

    @Test
    void deletes_correct_amount() {
        NodeAgentContext context = NodeAgentContextImpl.builder(
                NodeSpec.Builder.testSpec("h123a.domain.tld").realResources(new NodeResources(1, 1, 1, 1)).build())
                .fileSystem(fileSystem).build();

        mockDiskUsage(950_000L);

        storageMaintainer.cleanDiskIfFull(context);
        // Allocated size: 1 GB, usage: 950_000 kiB (972.8 MB). Wanted usage: 70% => 700 MB
        verify(diskCleanup).cleanup(eq(context), any(), eq(272_800_000L));
    }

    @AfterEach
    public void after() {
        terminal.verifyAllCommandsExecuted();
    }

    private void mockDiskUsage(long kBytes) {
        terminal.expectCommand("du -xsk /data/vespa/storage/h123a 2>&1", 0, kBytes + "\t/path");
    }
}
