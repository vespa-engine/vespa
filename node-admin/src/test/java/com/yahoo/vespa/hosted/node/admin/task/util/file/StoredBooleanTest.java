// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author hakonhall
 */
public class StoredBooleanTest {
    private final TaskContext context = mock(TaskContext.class);
    private final FileSystem fileSystem = TestFileSystem.create();
    private final Path path = fileSystem.getPath("/foo");
    private final StoredBoolean storedBoolean = new StoredBoolean(path);

    @Test
    void storedBoolean() {
        assertFalse(storedBoolean.value());
        storedBoolean.set(context);
        assertTrue(storedBoolean.value());
        storedBoolean.clear(context);
        assertFalse(storedBoolean.value());
    }

    @Test
    void testCompatibility() throws IOException {
        StoredInteger storedInteger = new StoredInteger(path);
        assertFalse(storedBoolean.value());

        storedInteger.write(context, 1);
        assertTrue(storedBoolean.value());

        storedInteger.write(context, 2);
        assertTrue(storedBoolean.value());

        storedInteger.write(context, 0);
        assertFalse(storedBoolean.value());

        Files.delete(path);
        assertFalse(storedBoolean.value());
    }
}