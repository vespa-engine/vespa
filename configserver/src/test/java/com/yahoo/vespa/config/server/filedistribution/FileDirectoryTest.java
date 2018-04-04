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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        createFileInSubDir(subDirectory, "foo");
        FileReference fileReference = fileDirectory.addFile(subDirectory);
        File dir = fileDirectory.getFile(fileReference);
        assertTrue(dir.exists());
        assertTrue(new File(dir, "foo").exists());
        assertFalse(new File(dir, "doesnotexist").exists());
        assertEquals("1315a322fc323608", fileReference.value());


        // Add a file, should be available and file reference should have another value
        createFileInSubDir(subDirectory, "bar");
        fileReference = fileDirectory.addFile(subDirectory);
        dir = fileDirectory.getFile(fileReference);
        assertTrue(new File(dir, "foo").exists());
        assertTrue(new File(dir, "bar").exists());
        assertEquals("9ca074b47a4b510c", fileReference.value());
    }

    @Test
    public void requireThatExistingDirWithInvalidContentIsDeleted() throws IOException {
        FileDirectory fileDirectory = new FileDirectory(temporaryFolder.getRoot());

        String subdirName = "subdir";
        File subDirectory = new File(temporaryFolder.getRoot(), subdirName);
        createFileInSubDir(subDirectory, "foo");
        FileReference fileReference = fileDirectory.addFile(subDirectory);
        File dir = fileDirectory.getFile(fileReference);
        assertTrue(dir.exists());
        assertTrue(new File(dir, "foo").exists());
        assertFalse(new File(dir, "doesnotexist").exists());
        assertEquals("1315a322fc323608", fileReference.value());

        // Remove a file, directory should be deleted before adding a new file
        Files.delete(Paths.get(fileDirectory.getPath(fileReference)).resolve("subdir").resolve("foo"));
        fileReference = fileDirectory.addFile(subDirectory);
        dir = fileDirectory.getFile(fileReference);
        assertTrue(dir.exists());
        assertTrue(new File(dir, "foo").exists());
        assertFalse(new File(dir, "doesnotexist").exists());
        assertEquals("1315a322fc323608", fileReference.value());
    }

    // Content in created file is equal to the filename string
    private FileReference createFile(String filename) throws IOException {
        File file = temporaryFolder.newFile(filename);
        IOUtils.writeFile(file, filename, false);
        return fileDirectory.addFile(file);
    }

    private void createFileInSubDir(File subDirectory, String filename) throws IOException {
        if (!subDirectory.exists())
            subDirectory.mkdirs();
        File file = new File(subDirectory, filename);
        IOUtils.writeFile(file, filename, false);
    }

}


