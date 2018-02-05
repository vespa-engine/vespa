// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TestTaskContext;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Test;

import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author hakonhall
 */
public class MakeDirectoryTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final TestTaskContext context = new TestTaskContext();

    private String path = "/parent/dir";
    private String permissions = "rwxr----x";
    private String owner = "test-owner";
    private String group = "test-group";

    @Test
    public void newDirectory() {
        verifySystemModifications(
                "Creating directory " + path,
                "Changing owner of /parent/dir from user to test-owner",
                "Changing group of /parent/dir from group to test-group");

        owner = "new-owner";
        verifySystemModifications("Changing owner of /parent/dir from test-owner to new-owner");

        group = "new-group";
        verifySystemModifications("Changing group of /parent/dir from test-group to new-group");

        permissions = "--x---r--";
        verifySystemModifications("Changing permissions of /parent/dir from rwxr----x to --x---r--");
    }

    private void verifySystemModifications(String... modifications) {
        context.clearSystemModificationLog();
        MakeDirectory makeDirectory = new MakeDirectory(fileSystem.getPath(path))
                .createParents()
                .withPermissions(permissions)
                .withOwner(owner)
                .withGroup(group);
        assertTrue(makeDirectory.converge(context));

        assertEquals(Arrays.asList(modifications), context.getSystemModificationLog());

        context.clearSystemModificationLog();
        assertFalse(makeDirectory.converge(context));
        assertEquals(Collections.emptyList(), context.getSystemModificationLog());
    }

    @Test
    public void exceptionIfMissingParent() {
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
    public void okIfParentExists() {
        String path = "/dir";
        MakeDirectory makeDirectory = new MakeDirectory(fileSystem.getPath(path));
        assertTrue(makeDirectory.converge(context));
        assertTrue(Files.isDirectory(fileSystem.getPath(path)));

        MakeDirectory makeDirectory2 = new MakeDirectory(fileSystem.getPath(path));
        assertFalse(makeDirectory2.converge(context));
    }
}