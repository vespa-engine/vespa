// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task;

import org.glassfish.jersey.internal.util.Producer;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WriteFileTaskTest extends TaskTestBase {
    private final String content = "line1\nline2\n";

    @Test
    public void testWrite() {
        Path parentDirectory = Paths.get("/foo");
        Path path = parentDirectory.resolve("bar");

        @SuppressWarnings("unchecked")
        Producer<String> contentProducer = (Producer<String>) mock(Producer.class);
        when(contentProducer.call()).thenReturn(content);

        final String permissions = "rwxr-x---";
        final String owner = "owner";
        final String group = "group";

        WriteFileTask task = new WriteFileTask(path, contentProducer)
                .withPermissions(permissions)
                .withOwner(owner)
                .withGroup(group);

        when(fileSystemMock.isRegularFile(path)).thenReturn(false);
        when(contextMock.executeSubtask(any(MakeDirectoryTask.class))).thenReturn(false);

        assertTrue(task.execute(contextMock));

        verify(contentProducer, times(1)).call();
        verify(fileSystemMock).writeUtf8File(path, content);
        verify(fileSystemMock).setPermissions(path, permissions);
        verify(fileSystemMock).setOwner(path, owner);
        verify(fileSystemMock).setGroup(path, group);

        // Writing a file with the expected content
        ArgumentCaptor<MakeDirectoryTask> makeDirectoryTaskCaptor =
                ArgumentCaptor.forClass(MakeDirectoryTask.class);
        verify(contextMock, times(1))
                .executeSubtask(makeDirectoryTaskCaptor.capture());

        MakeDirectoryTask makeDirectoryTask = makeDirectoryTaskCaptor.getValue();
        assertEquals(parentDirectory, makeDirectoryTask.getPath());
        assertTrue(makeDirectoryTask.getWithParents());
    }

    @Test
    public void fileAlreadyExists() {
        Path path = Paths.get("foo");

        final String permissions = "rwxr-x---";
        final String owner = "owner";
        final String group = "group";

        WriteFileTask task = new WriteFileTask(path, () -> content)
                .withPermissions(permissions)
                .withOwner(owner)
                .withGroup(group);

        when(fileSystemMock.isRegularFile(path)).thenReturn(true);

        assertFalse(task.execute(contextMock));
   }
}