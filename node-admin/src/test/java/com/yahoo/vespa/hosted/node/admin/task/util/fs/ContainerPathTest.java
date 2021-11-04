// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.fs;

import com.yahoo.vespa.hosted.node.admin.nodeagent.UserScope;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixUser;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author freva
 */
class ContainerPathTest {

    private final FileSystem baseFs = TestFileSystem.create();
    private final ContainerFileSystem containerFs = ContainerFileSystem.create(baseFs.getPath("/data/storage/ctr1"), mock(UserScope.class));

    @Test
    public void create_new_container_path() {
        ContainerPath path = fromPathInContainer(Path.of("/opt/vespa//logs/./file"));
        assertPaths(path, "/data/storage/ctr1/opt/vespa/logs/file", "/opt/vespa/logs/file");

        path = fromPathOnHost(baseFs.getPath("/data/storage/ctr1/opt/vespa/logs/file"));
        assertPaths(path, "/data/storage/ctr1/opt/vespa/logs/file", "/opt/vespa/logs/file");

        path = fromPathOnHost(baseFs.getPath("/data/storage/ctr2/..////./ctr1/./opt"));
        assertPaths(path, "/data/storage/ctr1/opt", "/opt");

        assertThrows(() -> fromPathInContainer(Path.of("relative/path")), "Path in container must be absolute: relative/path");
        assertThrows(() -> fromPathOnHost(baseFs.getPath("relative/path")), "Paths have different roots: /data/storage/ctr1, relative/path");
        assertThrows(() -> fromPathOnHost(baseFs.getPath("/data/storage/ctr2")), "Path /data/storage/ctr2 is not under container root /data/storage/ctr1");
        assertThrows(() -> fromPathOnHost(baseFs.getPath("/data/storage/ctr1/../ctr2")), "Path /data/storage/ctr2 is not under container root /data/storage/ctr1");
    }

    @Test
    public void container_path_operations() {
        ContainerPath path = fromPathInContainer(Path.of("/opt/vespa/logs/file"));
        ContainerPath parent = path.getParent();
        assertPaths(path.getRoot(), "/data/storage/ctr1", "/");
        assertPaths(parent, "/data/storage/ctr1/opt/vespa/logs", "/opt/vespa/logs");
        assertNull(path.getRoot().getParent());

        assertEquals(Path.of("file"), path.getFileName());
        assertEquals(Path.of("logs"), path.getName(2));
        assertEquals(4, path.getNameCount());
        assertEquals(Path.of("vespa/logs"), path.subpath(1, 3));

        assertTrue(path.startsWith(path));
        assertTrue(path.startsWith(parent));
        assertFalse(parent.startsWith(path));
        assertFalse(path.startsWith(Path.of(path.toString())));

        assertTrue(path.endsWith(Path.of(path.pathInContainer())));
        assertTrue(path.endsWith(Path.of("logs/file")));
        assertFalse(path.endsWith(Path.of("/logs/file")));
    }

    @Test
    public void resolution()  {
        ContainerPath path = fromPathInContainer(Path.of("/opt/vespa/logs"));
        assertPaths(path.resolve(Path.of("/root")), "/data/storage/ctr1/root", "/root");
        assertPaths(path.resolve(Path.of("relative")), "/data/storage/ctr1/opt/vespa/logs/relative", "/opt/vespa/logs/relative");
        assertPaths(path.resolve(Path.of("/../../../dir2/../../../dir2")), "/data/storage/ctr1/dir2", "/dir2");
        assertPaths(path.resolve(Path.of("/some/././///path")), "/data/storage/ctr1/some/path", "/some/path");

        assertPaths(path.resolve(Path.of("../dir")), "/data/storage/ctr1/opt/vespa/dir", "/opt/vespa/dir");
        assertEquals(path.resolve(Path.of("../dir")), path.resolveSibling("dir"));
    }

    @Test
    public void resolves_real_paths() throws IOException {
        ContainerPath path = fromPathInContainer(Path.of("/opt/vespa/logs"));
        Files.createDirectories(path.pathOnHost().getParent());

        Files.createFile(baseFs.getPath("/data/storage/ctr1/opt/vespa/target1"));
        Files.createSymbolicLink(path.pathOnHost(), path.pathOnHost().resolveSibling("target1"));
        assertPaths(path.toRealPath(LinkOption.NOFOLLOW_LINKS), "/data/storage/ctr1/opt/vespa/logs", "/opt/vespa/logs");
        assertPaths(path.toRealPath(), "/data/storage/ctr1/opt/vespa/target1", "/opt/vespa/target1");

        Files.delete(path.pathOnHost());
        Files.createFile(baseFs.getPath("/data/storage/ctr1/opt/target2"));
        Files.createSymbolicLink(path.pathOnHost(), baseFs.getPath("../target2"));
        assertPaths(path.toRealPath(), "/data/storage/ctr1/opt/target2", "/opt/target2");

        Files.delete(path.pathOnHost());
        Files.createFile(baseFs.getPath("/data/storage/ctr2"));
        Files.createSymbolicLink(path.pathOnHost(), path.getRoot().pathOnHost().resolveSibling("ctr2"));
        assertThrows(path::toRealPath, "Path /data/storage/ctr2 is not under container root /data/storage/ctr1");
    }

    private ContainerPath fromPathInContainer(Path pathInContainer) {
        return ContainerPath.fromPathInContainer(containerFs, pathInContainer, UnixUser.ROOT);
    }
    private ContainerPath fromPathOnHost(Path pathOnHost) {
        return ContainerPath.fromPathOnHost(containerFs, pathOnHost, UnixUser.ROOT);
    }

    private static void assertPaths(ContainerPath actual, String expectedPathOnHost, String expectedPathInContainer) {
        assertEquals(expectedPathOnHost, actual.pathOnHost().toString());
        assertEquals(expectedPathInContainer, actual.pathInContainer());
    }

    private static void assertThrows(Executable executable, String expectedMsg) {
        String actualMsg = Assertions.assertThrows(IllegalArgumentException.class, executable).getMessage();
        assertEquals(expectedMsg, actualMsg);
    }
}