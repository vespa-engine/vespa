// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.test.file.TestFileSystem;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import org.junit.Test;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class FileWriterTest {
    private final FileSystem fileSystem = TestFileSystem.create();

    @Test
    public void testWrite() {
        final String content = "content";
        final String permissions = "rwxr-xr-x";
        final String owner = "owner";
        final String group = "group";

        Path path = fileSystem.getPath("/opt/vespa/tmp/file.txt");
        FileWriter writer = new FileWriter(path, () -> content)
                .withPermissions(permissions)
                .withOwner(owner)
                .withGroup(group)
                .onlyIfFileDoesNotAlreadyExist();
        TaskContext context = mock(TaskContext.class);
        assertTrue(writer.converge(context));
        verify(context, times(1)).logSystemModification(any(), eq("Creating file " + path));

        UnixPath unixPath = new UnixPath(path);
        assertEquals(content, unixPath.readUtf8File());
        assertEquals(permissions, unixPath.getPermissions());
        assertEquals(owner, unixPath.getOwner());
        assertEquals(group, unixPath.getGroup());
        Instant fileTime = unixPath.getLastModifiedTime();

        // Second time is a no-op.
        assertFalse(writer.converge(context));
        assertEquals(fileTime, unixPath.getLastModifiedTime());
    }
}
