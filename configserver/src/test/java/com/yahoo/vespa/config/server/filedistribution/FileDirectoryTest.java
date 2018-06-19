// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class FileDirectoryTest {

    private FileDirectory fileDirectory;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() {
        fileDirectory = new FileDirectory(temporaryFolder.getRoot());
    }

    @Test
    public void requireThatFileReferenceWithFilesWorks() throws IOException {
        FileReference foo = createFile("foo");
        FileReference bar = createFile("bar");

        assertTrue(fileDirectory.getFile(foo).exists());
        assertEquals("ea315b7acac56246", foo.value());
        assertTrue(fileDirectory.getFile(bar).exists());
        assertEquals("2b8e97f15c854e1d", bar.value());
    }

    @Test
    public void requireThatFileReferenceWithSubDirectoriesWorks() throws IOException {
        FileDirectory fileDirectory = new FileDirectory(temporaryFolder.getRoot());

        String subdirName = "subdir";
        File subDirectory = new File(temporaryFolder.getRoot(), subdirName);
        createFileInSubDir(subDirectory, "foo", "some content");
        FileReference fileReference = fileDirectory.addFile(subDirectory);
        File dir = fileDirectory.getFile(fileReference);
        assertTrue(dir.exists());
        assertTrue(new File(dir, "foo").exists());
        assertFalse(new File(dir, "doesnotexist").exists());
        assertEquals("bebc5a1aee74223d", fileReference.value());

        // Change contents of a file, file reference value should change
        createFileInSubDir(subDirectory, "foo", "new content");
        FileReference fileReference2 = fileDirectory.addFile(subDirectory);
        dir = fileDirectory.getFile(fileReference2);
        assertTrue(new File(dir, "foo").exists());
        assertNotEquals(fileReference + " should not be equal to " + fileReference2, fileReference, fileReference2);
        assertEquals("e5d4b3fe5ee3ede3", fileReference2.value());

        // Add a file, should be available and file reference should have another value
        createFileInSubDir(subDirectory, "bar", "some other content");
        FileReference fileReference3 = fileDirectory.addFile(subDirectory);
        dir = fileDirectory.getFile(fileReference3);
        assertTrue(new File(dir, "foo").exists());
        assertTrue(new File(dir, "bar").exists());
        assertEquals("894bced3fc9d199b", fileReference3.value());
    }

    @Test
    public void requireThatExistingDirWithInvalidContentIsDeleted() throws IOException {
        FileDirectory fileDirectory = new FileDirectory(temporaryFolder.getRoot());

        String subdirName = "subdir";
        File subDirectory = new File(temporaryFolder.getRoot(), subdirName);
        createFileInSubDir(subDirectory, "foo", "some content");
        FileReference fileReference = fileDirectory.addFile(subDirectory);
        File dir = fileDirectory.getFile(fileReference);
        assertTrue(dir.exists());
        File foo = new File(dir, "foo");
        assertTrue(foo.exists());
        FileTime fooCreatedTimestamp = Files.readAttributes(foo.toPath(), BasicFileAttributes.class).creationTime();
        assertFalse(new File(dir, "doesnotexist").exists());
        assertEquals("bebc5a1aee74223d", fileReference.value());

        // Remove a file, directory should be deleted before adding a new file
        try { Thread.sleep(1000);} catch (InterruptedException e) {/*ignore */} // Needed since we have timestamp resolution of 1 second
        Files.delete(Paths.get(fileDirectory.getPath(fileReference)).resolve("subdir").resolve("foo"));
        fileReference = fileDirectory.addFile(subDirectory);
        dir = fileDirectory.getFile(fileReference);
        File foo2 = new File(dir, "foo");
        assertTrue(dir.exists());
        assertTrue(foo2.exists());
        FileTime foo2CreatedTimestamp = Files.readAttributes(foo2.toPath(), BasicFileAttributes.class).creationTime();
        // Check that creation timestamp is newer than the old one to be sure that a new file was written
        assertTrue(foo2CreatedTimestamp.compareTo(fooCreatedTimestamp) > 0);
        assertFalse(new File(dir, "doesnotexist").exists());
        assertEquals("bebc5a1aee74223d", fileReference.value());
    }

    // Content in created file is equal to the filename string
    private FileReference createFile(String filename) throws IOException {
        File file = temporaryFolder.newFile(filename);
        IOUtils.writeFile(file, filename, false);
        return fileDirectory.addFile(file);
    }

    private void createFileInSubDir(File subDirectory, String filename, String fileContent) throws IOException {
        if (!subDirectory.exists())
            subDirectory.mkdirs();
        File file = new File(subDirectory, filename);
        IOUtils.writeFile(file, fileContent, false);
    }

}


