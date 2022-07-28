// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.nio.file.FileSystem;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hakonhall
 */
public class FileSnapshotTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final UnixPath path = new UnixPath(fileSystem.getPath("/var/lib/file.txt"));

    private FileSnapshot fileSnapshot = FileSnapshot.forPath(path.toPath());

    @Test
    void fileDoesNotExist() {
        assertFalse(fileSnapshot.exists());
        assertFalse(fileSnapshot.attributes().isPresent());
        assertFalse(fileSnapshot.content().isPresent());
        assertEquals(path.toPath(), fileSnapshot.path());
    }

    @Test
    void directory() {
        path.createParents().createDirectory();
        fileSnapshot = fileSnapshot.snapshot();
        assertTrue(fileSnapshot.exists());
        assertTrue(fileSnapshot.attributes().isPresent());
        assertTrue(fileSnapshot.attributes().get().isDirectory());
    }

    @Test
    void regularFile() {
        path.createParents().writeUtf8File("file content");
        fileSnapshot = fileSnapshot.snapshot();
        assertTrue(fileSnapshot.exists());
        assertTrue(fileSnapshot.attributes().isPresent());
        assertTrue(fileSnapshot.attributes().get().isRegularFile());
        assertTrue(fileSnapshot.utf8Content().isPresent());
        assertEquals("file content", fileSnapshot.utf8Content().get());

        FileSnapshot newFileSnapshot = fileSnapshot.snapshot();
        assertSame(fileSnapshot, newFileSnapshot);
    }

    @Test
    void fileRemoval() {
        path.createParents().writeUtf8File("file content");
        fileSnapshot = fileSnapshot.snapshot();
        assertTrue(fileSnapshot.exists());
        path.deleteIfExists();
        fileSnapshot = fileSnapshot.snapshot();
        assertFalse(fileSnapshot.exists());
    }
}