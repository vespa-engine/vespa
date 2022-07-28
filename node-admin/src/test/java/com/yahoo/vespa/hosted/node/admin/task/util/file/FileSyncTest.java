// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TestTaskContext;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FileSyncTest {
    private final TestTaskContext taskContext = new TestTaskContext();
    private final FileSystem fileSystem = TestFileSystem.create();

    private final Path path = fileSystem.getPath("/dir/file.txt");
    private final UnixPath unixPath = new UnixPath(path);
    private final FileSync fileSync = new FileSync(path);

    private String content = "content";
    private int ownerId = 123; // default is 1
    private int groupId = 456; // default is 2
    private String permissions = "rw-r-xr--";

    @Test
    void trivial() {
        assertConvergence("Creating file /dir/file.txt",
                "Changing user ID of /dir/file.txt from 1 to 123",
                "Changing group ID of /dir/file.txt from 2 to 456",
                "Changing permissions of /dir/file.txt from rw-r--r-- to rw-r-xr--");

        content = "new-content";
        assertConvergence("Patching file /dir/file.txt");

        ownerId = 124;
        assertConvergence("Changing user ID of /dir/file.txt from 123 to 124");

        groupId = 457;
        assertConvergence("Changing group ID of /dir/file.txt from 456 to 457");

        permissions = "rwxr--rwx";
        assertConvergence("Changing permissions of /dir/file.txt from rw-r-xr-- to " +
                permissions);
    }

    private void assertConvergence(String... systemModificationMessages) {
        PartialFileData fileData = PartialFileData.builder()
                .withContent(content)
                .withOwnerId(ownerId)
                .withGroupId(groupId)
                .withPermissions(permissions)
                .create();
        taskContext.clearSystemModificationLog();
        assertTrue(fileSync.convergeTo(taskContext, fileData));

        assertTrue(Files.isRegularFile(path));
        fileData.getContent().ifPresent(content -> assertArrayEquals(content, unixPath.readBytes()));
        fileData.getOwnerId().ifPresent(owner -> assertEquals((int) owner, unixPath.getOwnerId()));
        fileData.getGroupId().ifPresent(group -> assertEquals((int) group, unixPath.getGroupId()));
        fileData.getPermissions().ifPresent(permissions -> assertEquals(permissions, unixPath.getPermissions()));

        List<String> actualMods = taskContext.getSystemModificationLog();
        List<String> expectedMods = List.of(systemModificationMessages);
        assertEquals(expectedMods, actualMods);

        UnixPath unixPath = new UnixPath(path);
        Instant lastModifiedTime = unixPath.getLastModifiedTime();
        taskContext.clearSystemModificationLog();
        assertFalse(fileSync.convergeTo(taskContext, fileData));
        assertEquals(lastModifiedTime, unixPath.getLastModifiedTime());

        actualMods = taskContext.getSystemModificationLog();
        assertEquals(new ArrayList<>(), actualMods);
    }
}
