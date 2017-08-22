// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.google.common.collect.ImmutableList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author bratseth
 */
public class ZipStreamReader {
    
    private final ImmutableList<ZipEntryWithContent> entries;

    public ZipStreamReader(InputStream input) {
        try (ZipInputStream zipInput = new ZipInputStream(input)) {
            ImmutableList.Builder<ZipEntryWithContent> builder = new ImmutableList.Builder<>();
            ZipEntry zipEntry;
            while (null != (zipEntry = zipInput.getNextEntry()))
                builder.add(new ZipEntryWithContent(zipEntry, readContent(zipInput)));
            entries = builder.build();
        }
        catch (IOException e) {
            throw new IllegalArgumentException("IO error reading zip content", e);
        }
    }
    
    private byte[] readContent(ZipInputStream zipInput) {
        try (ByteArrayOutputStream bis = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int read;
            while ( -1 != (read = zipInput.read(buffer)))
                bis.write(buffer, 0, read);
            return bis.toByteArray();
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Failed reading from zipped content", e);
        }
    }
    
    public List<ZipEntryWithContent> entries() { return entries; }
    
    public static class ZipEntryWithContent {
        
        private final ZipEntry zipEntry;
        private final byte[] content;
        
        public ZipEntryWithContent(ZipEntry zipEntry, byte[] content) {
            this.zipEntry = zipEntry;
            this.content = content;
        }
        
        public ZipEntry zipEntry() { return zipEntry; }
        public byte[] content() { return content; }
        
    }
    
}
