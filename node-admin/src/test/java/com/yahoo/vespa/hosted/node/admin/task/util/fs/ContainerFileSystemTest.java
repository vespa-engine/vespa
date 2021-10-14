// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.fs;

import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerUserPrincipalLookupService.OVERFLOW_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author valerijf
 */
class ContainerFileSystemTest {

    private final FileSystem fileSystem = TestFileSystem.create();
    private final UnixPath containerRootOnHost = new UnixPath(fileSystem.getPath("/data/storage/ctr1"));
    private final ContainerFileSystem containerFs = ContainerFileSystem.create(containerRootOnHost.createDirectories().toPath(), 10_000, 11_000);

    @Test
    public void creates_files_and_directories_with_container_root_as_owner() throws IOException {
        ContainerPath containerPath = ContainerPath.fromPathInContainer(containerFs, Path.of("/opt/vespa/logs/file"));
        UnixPath unixPath = new UnixPath(containerPath).createParents().writeUtf8File("hello world");

        for (ContainerPath p = containerPath; p.getParent() != null; p = p.getParent())
            assertOwnership(p, 0, 0, 10000, 11000);

        unixPath.setOwnerId(500).setGroupId(1000);
        assertOwnership(containerPath, 500, 1000, 10500, 12000);

        UnixPath hostFile = new UnixPath(fileSystem.getPath("/file")).createNewFile();
        ContainerPath destination = ContainerPath.fromPathInContainer(containerFs, Path.of("/copy1"));
        Files.copy(hostFile.toPath(), destination);
        assertOwnership(destination, 0, 0, 10000, 11000);
    }

    @Test
    public void copy() throws IOException {
        UnixPath hostFile = new UnixPath(fileSystem.getPath("/file")).createNewFile();
        ContainerPath destination = ContainerPath.fromPathInContainer(containerFs, Path.of("/dest"));

        // If file is copied to JimFS path, the UID/GIDs are not fixed
        Files.copy(hostFile.toPath(), destination.pathOnHost());
        assertEquals(String.valueOf(OVERFLOW_ID), Files.getOwner(destination).getName());
        Files.delete(destination);

        Files.copy(hostFile.toPath(), destination);
        assertOwnership(destination, 0, 0, 10000, 11000);
    }

    @Test
    public void move() throws IOException {
        UnixPath hostFile = new UnixPath(fileSystem.getPath("/file")).createNewFile();
        ContainerPath destination = ContainerPath.fromPathInContainer(containerFs, Path.of("/dest"));

        // If file is moved to JimFS path, the UID/GIDs are not fixed
        Files.move(hostFile.toPath(), destination.pathOnHost());
        assertEquals(String.valueOf(OVERFLOW_ID), Files.getOwner(destination).getName());
        Files.delete(destination);

        hostFile.createNewFile();
        Files.move(hostFile.toPath(), destination);
        assertOwnership(destination, 0, 0, 10000, 11000);
    }

    private static void assertOwnership(ContainerPath path, int contUid, int contGid, int hostUid, int hostGid) throws IOException {
        assertOwnership(path, contUid, contGid);
        assertOwnership(path.pathOnHost(), hostUid, hostGid);
    }

    private static void assertOwnership(Path path, int uid, int gid) throws IOException {
        Map<String, Object> attrs = Files.readAttributes(path, "unix:*");
        assertEquals(uid, attrs.get("uid"));
        assertEquals(gid, attrs.get("gid"));
    }
}
