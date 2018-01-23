// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task;

import com.yahoo.vespa.hosted.node.admin.io.FileSystem;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MakeDirectoryTaskTest {
    private final FileSystem fileSystem = mock(FileSystem.class);
    private final Task.TaskContext context = mock(Task.TaskContext.class);
    private final Path root = Paths.get("/");
    private final Path fooDir = root.resolve("foo");
    private final Path barDir = fooDir.resolve("bar");
    private final MakeDirectoryTask task = new MakeDirectoryTask(barDir);

    @Before
    public void setUp() {
        when(context.getFileSystem()).thenReturn(fileSystem);
    }

    @Test
    public void directoryExists() {
        when(fileSystem.isDirectory(barDir)).thenReturn(true);
        assertFalse(task.execute(context));
    }

    @Test
    public void withParents() {
        when(fileSystem.isDirectory(barDir)).thenReturn(false);
        when(fileSystem.isDirectory(fooDir)).thenReturn(false);
        when(fileSystem.isDirectory(root)).thenReturn(true);
        assertTrue(task.withParents().execute(context));

        InOrder inOrder = inOrder(fileSystem);
        inOrder.verify(fileSystem).createDirectory(fooDir);
        inOrder.verify(fileSystem).createDirectory(barDir);
        inOrder.verifyNoMoreInteractions();
    }
}