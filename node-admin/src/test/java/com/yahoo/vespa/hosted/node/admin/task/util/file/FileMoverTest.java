// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.NoSuchFileException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

/**
 * @author hakonhall
 */
class FileMoverTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final TaskContext context = mock(TaskContext.class);
    private final UnixPath source = new UnixPath(fileSystem.getPath("/from/source"));
    private final UnixPath destination = new UnixPath(fileSystem.getPath("/to/destination"));
    private final FileMover mover = new FileMover(source.toPath(), destination.toPath());

    @Test
    void movingRegularFile() {
        assertConvergeThrows(() -> mover.converge(context), NoSuchFileException.class, "/from/source");

        source.createParents().writeUtf8File("content");
        assertConvergeThrows(() -> mover.converge(context), NoSuchFileException.class, "/to/destination");

        destination.createParents();
        assertTrue(mover.converge(context));
        assertFalse(source.exists());
        assertTrue(destination.exists());
        assertEquals("content", destination.readUtf8File());

        assertFalse(mover.converge(context));

        source.writeUtf8File("content 2");
        assertConvergeThrows(() -> mover.converge(context), FileAlreadyExistsException.class, "/to/destination");

        mover.replaceExisting();
        assertTrue(mover.converge(context));

        source.writeUtf8File("content 3");
        destination.deleteIfExists();
        destination.createDirectory();
        assertTrue(mover.converge(context));
    }

    private void assertConvergeThrows(Runnable runnable, Class<?> expectedRootExceptionClass, String expectedMessage) {
        try {
            runnable.run();
            fail();
        } catch (Throwable t) {
            Throwable rootCause = t;
            do {
                Throwable cause = rootCause.getCause();
                if (cause == null) break;
                rootCause = cause;
            } while (true);

            assertTrue(expectedRootExceptionClass.isInstance(rootCause), "Unexpected root cause: " + rootCause);
            assertEquals(expectedMessage, rootCause.getMessage());
        }
    }
}