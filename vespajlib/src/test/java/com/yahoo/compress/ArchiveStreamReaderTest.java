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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author mpolden
 */
class ArchiveStreamReaderTest {

    @Test
    void reading() {
        Map<String, String> zipFiles = Map.of("foo", "contents of foo",
                                              "bar", "contents of bar",
                                              "baz", "0".repeat(2049));
        Map<String, String> zipContents = new HashMap<>(zipFiles);
        zipContents.put("dir/", ""); // Directories are always ignored
        Map<String, String> extracted = readAll(zip(zipContents), Options.standard());
        assertEquals(zipFiles, extracted);
    }

    @Test
    void entry_size_limit() {
        Map<String, String> entries = Map.of("foo.xml", "foobar");
        Options options = Options.standard().pathPredicate("foo.xml"::equals).maxEntrySize(1);
        try {
            readAll(zip(entries), options);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {}

        entries = Map.of("foo.xml", "foobar",
                         "foo.jar", "0".repeat(100) // File not extracted and thus not subject to size limit
        );
        Map<String, String> extracted = readAll(zip(entries), options.maxEntrySize(10));
        assertEquals(Map.of("foo.xml", "foobar"), extracted);
    }

    @Test
    void size_limit() {
        Map<String, String> entries = Map.of("foo.xml", "foo", "bar.xml", "bar");
        try {
            readAll(zip(entries), Options.standard().maxSize(4));
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {}
    }

    @Test
    void entry_limit() {
        Map<String, String> entries = Map.of("foo.xml", "foo", "bar.xml", "bar");
        try {
            readAll(zip(entries), Options.standard().maxEntries(1));
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {}
    }

    @Test
    void paths() {
        Map<String, Boolean> tests = Map.of(
                "../../services.xml", true,
                "/../.././services.xml", true,
                "./application/././services.xml", true,
                "application//services.xml", true,
                "artifacts/", false, // empty dir
                "services..xml", false,
                "application/services.xml", false,
                "components/foo-bar-deploy.jar", false,
                "services.xml", false
        );

        Options options = Options.standard().maxEntrySize(1024);
        tests.forEach((name, expectException) -> {
            try {
                readAll(zip(Map.of(name, "foo")), options.pathPredicate(name::equals));
                assertFalse(expectException, "Expected exception for '" + name + "'");
            } catch (IllegalArgumentException ignored) {
                assertTrue(expectException, "Unexpected exception for '" + name + "'");
            }
        });
    }

    private static Map<String, String> readAll(InputStream inputStream, Options options) {
        ArchiveStreamReader reader = ArchiveStreamReader.ofZip(inputStream, options);
        ArchiveStreamReader.ArchiveFile file;
        Map<String, String> entries = new HashMap<>();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((file = reader.readNextTo(baos)) != null) {
            entries.put(file.path().toString(), baos.toString(StandardCharsets.UTF_8));
            baos.reset();
        }
        return entries;
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
