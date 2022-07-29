// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application.pkg;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackageDiff.diff;
import static com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackageDiff.diffAgainstEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author freva
 */
public class ApplicationPackageDiffTest {
    private static final ApplicationPackage app1 = applicationPackage(Map.of("file1", "contents of the\nfirst file", "dir/myfile", "Second file", "dir/binary", "øøøø"));
    private static final ApplicationPackage app2 = applicationPackage(Map.of("file1", "updated contents\nof the\nfirst file\nafter some changes", "dir/myfile2", "Second file", "dir/binary", "øøøø"));

    @Test
    void no_diff() {
        assertEquals("No diff\n", new String(diff(app1, app1)));
    }

    @Test
    void diff_against_empty() {
        assertEquals("--- dir/binary\n" +
                "Diff skipped: File is binary (new file -> 8B)\n" +
                "\n" +
                "--- dir/myfile\n" +
                "@@ -1,0 +1,1 @@\n" +
                "+ Second file\n" +
                "\n" +
                "--- file1\n" +
                "@@ -1,0 +1,2 @@\n" +
                "+ contents of the\n" +
                "+ first file\n" +
                "\n", new String(diffAgainstEmpty(app1)));
    }

    @Test
    void full_diff() {
        // Even though dir/binary is binary file, we can see they are identical, so it should not print "Diff skipped"
        assertEquals("--- dir/myfile\n" +
                "@@ -1,1 +1,0 @@\n" +
                "- Second file\n" +
                "\n" +
                "--- dir/myfile2\n" +
                "@@ -1,0 +1,1 @@\n" +
                "+ Second file\n" +
                "\n" +
                "--- file1\n" +
                "@@ -1,2 +1,4 @@\n" +
                "+ updated contents\n" +
                "+ of the\n" +
                "- contents of the\n" +
                "  first file\n" +
                "+ after some changes\n" +
                "\n", new String(diff(app1, app2)));
    }

    @Test
    void skips_diff_for_too_large_files() {
        assertEquals("--- dir/myfile\n" +
                "@@ -1,1 +1,0 @@\n" +
                "- Second file\n" +
                "\n" +
                "--- dir/myfile2\n" +
                "@@ -1,0 +1,1 @@\n" +
                "+ Second file\n" +
                "\n" +
                "--- file1\n" +
                "Diff skipped: File too large (26B -> 53B)\n" +
                "\n", new String(diff(app1, app2, 12, 1000, 1000)));
    }

    @Test
    void skips_diff_if_file_diff_is_too_large() {
        assertEquals("--- dir/myfile\n" +
                "@@ -1,1 +1,0 @@\n" +
                "- Second file\n" +
                "\n" +
                "--- dir/myfile2\n" +
                "@@ -1,0 +1,1 @@\n" +
                "+ Second file\n" +
                "\n" +
                "--- file1\n" +
                "Diff skipped: Diff too large (96B)\n" +
                "\n", new String(diff(app1, app2, 1000, 50, 1000)));
    }

    @Test
    void skips_diff_if_total_diff_is_too_large() {
        assertEquals("--- dir/myfile\n" +
                "@@ -1,1 +1,0 @@\n" +
                "- Second file\n" +
                "\n" +
                "--- dir/myfile2\n" +
                "Diff skipped: Total diff size >20B)\n" +
                "\n" +
                "--- file1\n" +
                "Diff skipped: Total diff size >20B)\n" +
                "\n", new String(diff(app1, app2, 1000, 1000, 20)));
    }

    private static ApplicationPackage applicationPackage(Map<String, String> files) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(baos)) {
            out.setLevel(Deflater.NO_COMPRESSION); // This is for testing purposes so we skip compression for performance
            for (Map.Entry<String, String> file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                out.putNextEntry(entry);
                out.write(file.getValue().getBytes(UTF_8));
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ApplicationPackage(baos.toByteArray());
    }
}
