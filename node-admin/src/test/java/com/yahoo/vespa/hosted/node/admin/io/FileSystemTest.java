// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.io;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileSystemTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private Path root;
    private Path path;

    private final FileSystem fileSystem = new FileSystem();

    @Before
    public void setUp() throws Exception {
        root = folder.getRoot().toPath();
        path = folder.newFile().toPath();
    }

    @Test
    public void isDirectory() throws Exception {
        assertTrue(fileSystem.isDirectory(root));
        assertFalse(fileSystem.isDirectory(path));
    }

    @Test
    public void isRegularFile() throws Exception {
        assertTrue(fileSystem.isRegularFile(path));
        assertFalse(fileSystem.isRegularFile(root));
    }

    @Test
    public void createDirectory() throws Exception {
        Path dir = root.resolve("subdir");
        fileSystem.createDirectory(dir);
        assertTrue(fileSystem.isDirectory(dir));
    }

    @Test
    public void utf8FileIO() throws Exception {
        String original = "foo\nbar\n";
        Path path = root.resolve("example.txt");
        fileSystem.writeUtf8File(path, original);
        String fromFile = fileSystem.readUtf8File(path);
        assertEquals(original, fromFile);
    }

    @Test
    public void permissions() throws Exception {
        String expectedPermissions = "rwxr-x---";
        fileSystem.setPermissions(path, expectedPermissions);
        assertEquals(expectedPermissions, fileSystem.getPermissions(path));
    }

    @Test(expected = IllegalArgumentException.class)
    public void badPermissionsString() {
        fileSystem.setPermissions(path, "abcdefghi");
    }

    @Test
    public void owner() throws Exception {
        String owner = fileSystem.getOwner(path);
        fileSystem.setOwner(path, owner);
    }

    @Test
    public void group() throws Exception {
        String group = fileSystem.getGroup(path);
        fileSystem.setGroup(path, group);
    }
}