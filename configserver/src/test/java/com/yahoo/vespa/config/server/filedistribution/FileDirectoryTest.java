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
        assertTrue(fileDirectory.getFile(bar).exists());
    }


    @Test
    public void requireThatFileReferenceWithSubDirectoriesWorks() throws IOException {
        FileDirectory fileDirectory = new FileDirectory(temporaryFolder.getRoot());

        FileReference foo = createFileInSubDir("subdir", "foo");
        FileReference bar = createFileInSubDir("subdir", "bar");

        assertTrue(fileDirectory.getFile(foo).exists());
        assertTrue(fileDirectory.getFile(bar).exists());
    }

    // Content in created file is equal to the filename string
    private FileReference createFile(String filename) throws IOException {
        File file = temporaryFolder.newFile(filename);
        IOUtils.writeFile(file, filename, false);
        return fileDirectory.addFile(file);
    }

    private FileReference createFileInSubDir(String subdirName, String filename) throws IOException {
        File subDirectory = new File(temporaryFolder.getRoot(), subdirName);
        if (!subDirectory.exists())
            subDirectory.mkdirs();
        File file = new File(subDirectory, filename);
        IOUtils.writeFile(file, filename, false);
        return fileDirectory.addFile(file);
    }


}


