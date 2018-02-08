// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.FileSystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EditorTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final UnixPath path = new UnixPath(fileSystem.getPath("/file"));

    @Test
    public void testEdit() {
        path.writeUtf8File("first\n" +
                "second\n" +
                "third\n");

        LineEditor lineEditor = mock(LineEditor.class);
        when(lineEditor.edit(any())).thenReturn(
                LineEdit.none(), // don't edit the first line
                LineEdit.remove(), // remove the second
                LineEdit.replaceWith("replacement")); // replace the third

        Editor editor = new Editor(path.toPath(), lineEditor);
        TaskContext context = mock(TaskContext.class);

        assertTrue(editor.converge(context));

        verify(lineEditor, times(3)).edit(any());

        // Verify the system modification message
        ArgumentCaptor<String> modificationMessage = ArgumentCaptor.forClass(String.class);
        verify(context).recordSystemModification(any(), modificationMessage.capture());
        assertEquals(
                "Patching file /file:\n-second\n-third\n+replacement\n",
                modificationMessage.getValue());

        // Verify the new contents of the file:
        assertEquals("first\n" +
                "replacement\n", path.readUtf8File());
    }

    @Test
    public void noop() {
        path.writeUtf8File("line\n");

        LineEditor lineEditor = mock(LineEditor.class);
        when(lineEditor.edit(any())).thenReturn(LineEdit.none());

        Editor editor = new Editor(path.toPath(), lineEditor);
        TaskContext context = mock(TaskContext.class);

        assertFalse(editor.converge(context));

        verify(lineEditor, times(1)).edit(any());

        // Verify the system modification message
        verify(context, times(0)).recordSystemModification(any(), any());

        // Verify same contents
        assertEquals("line\n", path.readUtf8File());
    }
}