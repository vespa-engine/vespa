// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.io.IOUtils;
import com.yahoo.text.Utf8;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileReferenceDataTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testFileReferenceDataWithTempFile() throws IOException {
        String content = "blob";
        File tempFile = writeTempFile(content);
        FileReferenceData fileReferenceData =
                new LazyTemporaryStorageFileReferenceData(new FileReference("ref"), "foo", FileReferenceData.Type.compressed, tempFile);
        ByteBuffer byteBuffer = ByteBuffer.allocate(100);
        assertEquals(4, fileReferenceData.nextContent(byteBuffer));
        assertEquals(content, Utf8.toString(Arrays.copyOfRange(byteBuffer.array(), 0, 4)));

        // nextContent() will always return everything for FileReferenceDataBlob, so nothing more should be read
        assertEquals(-1, fileReferenceData.nextContent(byteBuffer));
        assertTrue(tempFile.exists());
        fileReferenceData.close();
        assertFalse(tempFile.exists()); // temp file should be removed when closing LazyTemporaryStorageFileReferenceData
    }

    @Test
    public void testFileReferenceData() throws IOException {
        String content = "blobbblubbblabb";
        File file = writeTempFile(content);
        FileReferenceData fileReferenceData =
                new LazyFileReferenceData(new FileReference("ref"), "foo", FileReferenceData.Type.compressed, file);
        ByteBuffer byteBuffer = ByteBuffer.allocate(10);
        assertEquals(10, fileReferenceData.nextContent(byteBuffer));
        assertEquals(content.substring(0,10), Utf8.toString(Arrays.copyOfRange(byteBuffer.array(), 0, 10)));
        byteBuffer.flip();
        assertEquals(5, fileReferenceData.nextContent(byteBuffer));
        assertEquals(content.substring(10,15), Utf8.toString(Arrays.copyOfRange(byteBuffer.array(), 0, 5)));

        // nextContent() will always return everything for FileReferenceDataBlob, so nothing more should be read
        assertEquals(-1, fileReferenceData.nextContent(byteBuffer));
        assertTrue(file.exists());
        fileReferenceData.close();
        assertTrue(file.exists()); // file should not be removed
    }

    private File writeTempFile(String content) throws IOException {
        File file = temporaryFolder.newFile();
        IOUtils.writeFile(file, Utf8.toBytes(content));
        return file;
    }

}
