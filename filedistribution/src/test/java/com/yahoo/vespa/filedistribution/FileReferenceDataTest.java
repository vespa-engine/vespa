// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.text.Utf8;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class FileReferenceDataTest {

    @Test
    public void testDataBlob() {
        String content = "blob";
        FileReferenceData fileReferenceData =
                new FileReferenceDataBlob(new FileReference("ref"), "foo", FileReferenceData.Type.compressed, Utf8.toBytes(content));
        ByteBuffer byteBuffer = ByteBuffer.allocate(100);
        assertEquals(4, fileReferenceData.nextContent(byteBuffer));
        assertEquals(content, Utf8.toString(Arrays.copyOfRange(byteBuffer.array(), 0, 4)));

        // nextContent() will always return everything for FileReferenceDataBlob, so nothing more should be read
        assertEquals(-1, fileReferenceData.nextContent(byteBuffer));
    }

    @Test
    public void testLargerDataBlob() {
        String content = "blobbblubbblabb";
        FileReferenceData fileReferenceData =
                new FileReferenceDataBlob(new FileReference("ref"), "foo", FileReferenceData.Type.compressed, Utf8.toBytes(content));
        ByteBuffer byteBuffer = ByteBuffer.allocate(10);
        assertEquals(10, fileReferenceData.nextContent(byteBuffer));
        assertEquals(content.substring(0,10), Utf8.toString(Arrays.copyOfRange(byteBuffer.array(), 0, 10)));
        byteBuffer.flip();
        assertEquals(5, fileReferenceData.nextContent(byteBuffer));
        assertEquals(content.substring(10,15), Utf8.toString(Arrays.copyOfRange(byteBuffer.array(), 0, 5)));

        // nextContent() will always return everything for FileReferenceDataBlob, so nothing more should be read
        assertEquals(-1, fileReferenceData.nextContent(byteBuffer));
    }

}
