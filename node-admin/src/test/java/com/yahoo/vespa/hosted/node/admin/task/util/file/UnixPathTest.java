// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author hakonhall
 */
public class UnixPathTest {

    private final FileSystem fs = TestFileSystem.create();

    @Test
    void createParents() {
        Path parentDirectory = fs.getPath("/a/b/c");
        Path filePath = parentDirectory.resolve("bar");
        UnixPath path = new UnixPath(filePath);

        assertFalse(Files.exists(fs.getPath("/a")));
        path.createParents();
        assertTrue(Files.exists(parentDirectory));
    }

    @Test
    void utf8File() {
        String original = "foo\nbar\n";
        UnixPath path = new UnixPath(fs.getPath("example.txt"));
        path.writeUtf8File(original);
        String fromFile = path.readUtf8File();
        assertEquals(original, fromFile);
        assertEquals(List.of("foo", "bar"), path.readLines());
    }

    @Test
    void touch() {
        UnixPath path = new UnixPath(fs.getPath("example.txt"));
        assertTrue(path.create());
        assertEquals("", path.readUtf8File());
        assertFalse(path.create());
    }

    @Test
    void permissions() {
        String expectedPermissions = "rwxr-x---";
        UnixPath path = new UnixPath(fs.getPath("file.txt"));
        path.writeUtf8File("foo");
        path.setPermissions(expectedPermissions);
        assertEquals(expectedPermissions, path.getPermissions());
    }

    @Test
    void badPermissionsString() {
        assertThrows(IllegalArgumentException.class, () -> {
            new UnixPath(fs.getPath("file.txt")).setPermissions("abcdefghi");
        });
    }

    @Test
    void owner() {
        Path path = fs.getPath("file.txt");
        UnixPath unixPath = new UnixPath(path);
        unixPath.writeUtf8File("foo");

        unixPath.setOwnerId(123);
        assertEquals(123, unixPath.getOwnerId());

        unixPath.setGroupId(456);
        assertEquals(456, unixPath.getGroupId());
    }

    @Test
    void createDirectoryWithPermissions() {
        Path path = fs.getPath("dir");
        UnixPath unixPath = new UnixPath(path);
        String permissions = "rwxr-xr--";
        assertTrue(unixPath.createDirectory(permissions));
        assertTrue(unixPath.isDirectory());
        assertEquals(permissions, unixPath.getPermissions());
        assertFalse(unixPath.createDirectory(permissions));
    }

    @Test
    void createSymbolicLink() {
        String original = "foo\nbar\n";
        UnixPath path = new UnixPath(fs.getPath("example.txt"));
        path.writeUtf8File(original);
        String fromFile = path.readUtf8File();
        assertEquals(original, fromFile);

        UnixPath link = path.createSymbolicLink(fs.getPath("link-to-example.txt"));
        assertEquals(original, link.readUtf8File());
    }

    @Test
    void readBytesIfExists() {
        UnixPath path = new UnixPath(fs.getPath("example.txt"));
        assertFalse(path.readBytesIfExists().isPresent());
        path.writeBytes(new byte[]{42});
        assertArrayEquals(new byte[]{42}, path.readBytesIfExists().get());
    }

    @Test
    void deleteRecursively() throws Exception {
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
        assertTrue(Files.exists(dir2), "Deleting " + link1 + " recursively does not remove " + dir2);
        assertTrue(Files.exists(file1), "Deleting " + link1 + " recursively does not remove " + file1);

        new UnixPath(dir1).deleteRecursively();
        assertFalse(Files.exists(file1), dir1 + " deleted recursively");
        assertFalse(Files.exists(dir2), dir1 + " deleted recursively");
        assertFalse(Files.exists(dir1), dir1 + " deleted recursively");
    }

    @Test
    void isEmptyDirectory() {
        var path = new UnixPath((fs.getPath("/foo")));
        assertFalse(path.isEmptyDirectory());

        path.writeUtf8File("");
        assertFalse(path.isEmptyDirectory());

        path.deleteIfExists();
        path.createDirectory();
        assertTrue(path.isEmptyDirectory());

        path.resolve("bar").writeUtf8File("");
        assertFalse(path.isEmptyDirectory());
    }

    @Test
    void atomicWrite() {
        var path = new UnixPath(fs.getPath("/dir/foo"));
        path.createParents();
        path.writeUtf8File("bar");
        path.atomicWriteBytes("bar v2".getBytes(StandardCharsets.UTF_8));
        assertEquals("bar v2", path.readUtf8File());
    }

    @Test
    void testParentAndFilename() {
        var absolutePath = new UnixPath("/foo/bar");
        assertEquals("/foo", absolutePath.getParent().toString());
        assertEquals("bar", absolutePath.getFilename());

        var pathWithoutSlash = new UnixPath("foo");
        assertRuntimeException(IllegalStateException.class, "Path has no parent directory: 'foo'", () -> pathWithoutSlash.getParent());
        assertEquals("foo", pathWithoutSlash.getFilename());

        var pathWithSlash = new UnixPath("/foo");
        assertEquals("/", pathWithSlash.getParent().toString());
        assertEquals("foo", pathWithSlash.getFilename());

        assertRuntimeException(IllegalStateException.class, "Path has no parent directory: '/'", () -> new UnixPath("/").getParent());
        assertRuntimeException(IllegalStateException.class, "Path has no filename: '/'", () -> new UnixPath("/").getFilename());
    }

    private <T extends RuntimeException> void assertRuntimeException(Class<T> baseClass, String message, Runnable runnable) {
        try {
            runnable.run();
            fail("No exception was thrown");
        } catch (RuntimeException e) {
            if (!baseClass.isInstance(e)) {
                throw new AssertionFailedError("Exception class mismatch", baseClass.getName(), e.getClass().getName());
            }

            assertEquals(message, e.getMessage());
        }
    }

}
