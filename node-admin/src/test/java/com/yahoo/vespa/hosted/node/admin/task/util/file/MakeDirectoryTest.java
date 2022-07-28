// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TestTaskContext;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hakonhall
 */
public class MakeDirectoryTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final TestTaskContext context = new TestTaskContext();

    private final String path = "/parent/dir";
    private String permissions = "rwxr----x";
    private int ownerId = 123;
    private int groupId = 456;

    @Test
    void newDirectory() {
        verifySystemModifications(
                "Creating directory " + path,
                "Changing user ID of /parent/dir from 1 to 123",
                "Changing group ID of /parent/dir from 2 to 456");

        ownerId = 124;
        verifySystemModifications("Changing user ID of /parent/dir from 123 to 124");

        groupId = 457;
        verifySystemModifications("Changing group ID of /parent/dir from 456 to 457");

        permissions = "--x---r--";
        verifySystemModifications("Changing permissions of /parent/dir from rwxr----x to --x---r--");
    }

    private void verifySystemModifications(String... modifications) {
        context.clearSystemModificationLog();
        MakeDirectory makeDirectory = new MakeDirectory(fileSystem.getPath(path))
                .createParents()
                .withPermissions(permissions)
                .withOwnerId(ownerId)
                .withGroupId(groupId);
        assertTrue(makeDirectory.converge(context));

        assertEquals(List.of(modifications), context.getSystemModificationLog());

        context.clearSystemModificationLog();
        assertFalse(makeDirectory.converge(context));
        assertEquals(List.of(), context.getSystemModificationLog());
    }

    @Test
    void exceptionIfMissingParent() {
        String path = "/parent/dir";
        MakeDirectory makeDirectory = new MakeDirectory(fileSystem.getPath(path));

        try {
            makeDirectory.converge(context);
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof NoSuchFileException) {
                return;
            }
            throw e;
        }
        fail();
    }

    @Test
    void okIfParentExists() {
        String path = "/dir";
        MakeDirectory makeDirectory = new MakeDirectory(fileSystem.getPath(path));
        assertTrue(makeDirectory.converge(context));
        assertTrue(Files.isDirectory(fileSystem.getPath(path)));

        MakeDirectory makeDirectory2 = new MakeDirectory(fileSystem.getPath(path));
        assertFalse(makeDirectory2.converge(context));
    }
}