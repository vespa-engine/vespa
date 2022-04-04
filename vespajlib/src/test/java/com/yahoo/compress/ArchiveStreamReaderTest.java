package com.yahoo.compress;

import com.yahoo.compress.ArchiveStreamReader.Options;
import com.yahoo.yolean.Exceptions;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
class ArchiveStreamReaderTest {

    @Test
    void reading() {
        Map<String, String> zipContents = Map.of("foo", "contents of foo",
                                                 "bar", "contents of bar",
                                                 "baz", "0".repeat(2049));
        ArchiveStreamReader reader = ArchiveStreamReader.ofZip(zip(zipContents), Options.standard());
        ArchiveStreamReader.ArchiveFile file;
        Map<String, String> extracted = new HashMap<>();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((file = reader.readNextTo(baos)) != null) {
            extracted.put(file.path().toString(), baos.toString(StandardCharsets.UTF_8));
            baos.reset();
        }
        assertEquals(zipContents, extracted);
    }

    private static InputStream zip(Map<String, String> entries) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipArchiveOutputStream archiveOutputStream = null;
        try {
            archiveOutputStream = new ZipArchiveOutputStream(baos);
            for (var kv : entries.entrySet()) {
                String entryName = kv.getKey();
                String contents = kv.getValue();
                ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
                archiveOutputStream.putArchiveEntry(entry);
                archiveOutputStream.write(contents.getBytes(StandardCharsets.UTF_8));
                archiveOutputStream.closeArchiveEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (archiveOutputStream != null) Exceptions.uncheck(archiveOutputStream::close);
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }

}
