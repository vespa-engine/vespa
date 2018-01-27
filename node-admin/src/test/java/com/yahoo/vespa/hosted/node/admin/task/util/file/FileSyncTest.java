// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import org.junit.Test;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileSyncTest {
    private final TestTaskContext taskContext = new TestTaskContext();
    private final FileSystem fileSystem = taskContext.fileSystem();

    private final Path path = fileSystem.getPath("/dir/file.txt");
    private final UnixPath unixPath = new UnixPath(path);
    private final FileSync fileSync = new FileSync(path);

    private String content = "content";
    private String owner = "owner"; // default is user
    private String group = "group1"; // default is group
    private String permissions = "rw-r-xr--";

    @Test
    public void trivial() {
        assertConvergence("Creating file /dir/file.txt",
                "Changing owner of /dir/file.txt from user to owner",
                "Changing group of /dir/file.txt from group to group1",
                "Changing permissions of /dir/file.txt from rw-r--r-- to rw-r-xr--");

        content = "new-content";
        assertConvergence("Patching file /dir/file.txt");

        owner = "new-owner";
        assertConvergence("Changing owner of /dir/file.txt from owner to " +
                        owner);

        group = "new-group1";
        assertConvergence("Changing group of /dir/file.txt from group1 to new-group1");

        permissions = "rwxr--rwx";
        assertConvergence("Changing permissions of /dir/file.txt from rw-r-xr-- to " +
                permissions);
    }

    private void assertConvergence(String... systemModificationMessages) {
        PartialFileData fileData = PartialFileData.builder()
                .withContent(content)
                .withOwner(owner)
                .withGroup(group)
                .withPermissions(permissions)
                .create();
        taskContext.clearSystemModificationLog();
        assertTrue(fileSync.convergeTo(taskContext, fileData));

        assertTrue(Files.isRegularFile(path));
        fileData.getContent().ifPresent(content -> assertEquals(content, unixPath.readUtf8File()));
        fileData.getOwner().ifPresent(owner -> assertEquals(owner, unixPath.getOwner()));
        fileData.getGroup().ifPresent(group -> assertEquals(group, unixPath.getGroup()));
        fileData.getPermissions().ifPresent(permissions -> assertEquals(permissions, unixPath.getPermissions()));

        List<String> actualMods = taskContext.getSystemModificationLog();
        List<String> expectedMods = Arrays.asList(systemModificationMessages);
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