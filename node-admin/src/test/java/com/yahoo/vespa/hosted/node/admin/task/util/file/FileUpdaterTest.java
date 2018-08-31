// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.FileSystem;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class FileUpdaterTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final UnixPath path = new UnixPath(fileSystem.getPath("/sys/kernel/mm/transparent_hugepage/enabled"));
    private final String wantedContent = "[always] madvise never";
    private final String writeContent = "enabled";
    private final FileUpdater writer = new FileUpdater(
            path.toPath(),
            content -> content.isPresent() && content.get().equals(wantedContent) ?
                    Optional.empty() : Optional.of(writeContent));
    private final TaskContext context = mock(TaskContext.class);

    @Before
    public void setUp() {
        path.createParents();
    }

    @Test
    public void testBasicWrite() {
        path.writeUtf8File("foo");

        assertTrue(writer.converge(context));
        assertEquals(writeContent, path.readUtf8File());

        // Simulate the content has been updated by the kernel
        path.writeUtf8File(wantedContent);

        assertFalse(writer.converge(context));
        assertEquals(wantedContent, path.readUtf8File());
    }

    @Test
    public void testAlreadyCorrectContent() {
        path.writeUtf8File(wantedContent);
        assertFalse(writer.converge(context));
        assertEquals(wantedContent, path.readUtf8File());
    }
}