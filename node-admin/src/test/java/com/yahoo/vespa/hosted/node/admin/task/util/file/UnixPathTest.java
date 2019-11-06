// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Test;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author hakonhall
 */
public class UnixPathTest {

    private final FileSystem fs = TestFileSystem.create();

    @Test
    public void createParents() {
        Path parentDirectory = fs.getPath("/a/b/c");
        Path filePath = parentDirectory.resolve("bar");
        UnixPath path = new UnixPath(filePath);

        assertFalse(Files.exists(fs.getPath("/a")));
        path.createParents();
        assertTrue(Files.exists(parentDirectory));
    }

    @Test
    public void utf8File() {
        String original = "foo\nbar\n";
        UnixPath path = new UnixPath(fs.getPath("example.txt"));
        path.writeUtf8File(original);
        String fromFile = path.readUtf8File();
        assertEquals(original, fromFile);
    }

    @Test
    public void permissions() {
        String expectedPermissions = "rwxr-x---";
        UnixPath path = new UnixPath(fs.getPath("file.txt"));
        path.writeUtf8File("foo");
        path.setPermissions(expectedPermissions);
        assertEquals(expectedPermissions, path.getPermissions());
    }

    @Test(expected = IllegalArgumentException.class)
    public void badPermissionsString() {
        new UnixPath(fs.getPath("file.txt")).setPermissions("abcdefghi");
    }

    @Test
    public void owner() {
        Path path = fs.getPath("file.txt");
        UnixPath unixPath = new UnixPath(path);
        unixPath.writeUtf8File("foo");

        unixPath.setOwner("owner");
        assertEquals("owner", unixPath.getOwner());

        unixPath.setGroup("group");
        assertEquals("group", unixPath.getGroup());
    }

    @Test
    public void createDirectoryWithPermissions() {
        Path path = fs.getPath("dir");
        UnixPath unixPath = new UnixPath(path);
        String permissions = "rwxr-xr--";
        unixPath.createDirectory(permissions);
        assertTrue(unixPath.isDirectory());
        assertEquals(permissions, unixPath.getPermissions());
    }

    @Test
    public void createSymbolicLink() {
        String original = "foo\nbar\n";
        UnixPath path = new UnixPath(fs.getPath("example.txt"));
        path.writeUtf8File(original);
        String fromFile = path.readUtf8File();
        assertEquals(original, fromFile);

        UnixPath link = path.createSymbolicLink(fs.getPath("link-to-example.txt"));
        assertEquals(original, link.readUtf8File());
    }

    @Test
    public void readBytesIfExists() {
        UnixPath path = new UnixPath(fs.getPath("example.txt"));
        assertFalse(path.readBytesIfExists().isPresent());
        path.writeBytes(new byte[]{42});
        assertArrayEquals(new byte[]{42}, path.readBytesIfExists().get());
    }

    @Test
    public void deleteRecursively() throws Exception {
        // Create the following file tree:
        //
        // /dir1
        //  |--- dir2
        //      |--- file1
        // /link1 -> /dir1/dir2
        //
        var dir1 = fs.getPath("/dir1");
        var dir2 = dir1.resolve("dir2");
        var file1 = dir2.resolve("file1");
        Files.createDirectories(dir2);
        Files.writeString(file1, "file1");
        var link1 = Files.createSymbolicLink(fs.getPath("/link1"), dir2);

        new UnixPath(link1).deleteRecursively();
        assertTrue("Deleting " + link1 + " recursively does not remove " + dir2, Files.exists(dir2));
        assertTrue("Deleting " + link1 + " recursively does not remove " + file1, Files.exists(file1));

        new UnixPath(dir1).deleteRecursively();
        assertFalse(dir1 + " deleted recursively", Files.exists(file1));
        assertFalse(dir1 + " deleted recursively", Files.exists(dir2));
        assertFalse(dir1 + " deleted recursively", Files.exists(dir1));
    }

}
