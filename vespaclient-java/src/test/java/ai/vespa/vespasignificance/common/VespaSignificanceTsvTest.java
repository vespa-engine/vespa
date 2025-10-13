// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.vespasignificance.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testing methods in {@link VespaSignificanceTsvReader} and {@link VespaSignificanceTsvWriter}.
 *
 * @author johsol
 */
public class VespaSignificanceTsvTest {
    @TempDir
    Path tmp;

    @Test
    void writeThenReadSuccess() throws Exception {
        Path f = tmp.resolve("df.vespa_significance_tsv");

        // Write header + rows (sorted = true)
        try (var w = new VespaSignificanceTsvWriter(f, 12345, true, Instant.parse("2025-10-13T12:05:00Z"))) {
            w.writeRow("beatles", 120);
            w.writeRow("coldplay", 532);
            w.writeRow("metallica", 488);
            w.writeRow("radiohead", 301);
            w.writeRow("red", 220);
            w.flush();
        }

        // Read streamingly
        try (var r = new VespaSignificanceTsvReader(f)) {
            var h = r.header();
            assertEquals(12345L, h.documentCount());
            assertTrue(h.sorted());
            assertEquals(Instant.parse("2025-10-13T12:05:00Z"), h.createdAt());

            Map<String, Long> seen = new LinkedHashMap<>();
            while (r.next()) {
                seen.put(r.term(), r.df());
            }

            assertEquals(5, seen.size());
            assertEquals(120L, seen.get("beatles"));
            assertEquals(532L, seen.get("coldplay"));
            assertEquals(488L, seen.get("metallica"));
            assertEquals(301L, seen.get("radiohead"));
            assertEquals(220L, seen.get("red"));
        }

        // ParseAll convenience
        var loaded = VespaSignificanceTsvReader.parseAll(f);
        assertEquals(12345L, loaded.header().documentCount());
        assertTrue(loaded.header().sorted());
        assertEquals(5, loaded.df().size());
        assertEquals(532L, loaded.df().get("coldplay"));
    }

    @Test
    void writerRejectsNonAscendingWhenSortedTrue() throws Exception {
        Path f = tmp.resolve("bad-sorted.vespa_significance_tsv");

        try (var w = new VespaSignificanceTsvWriter(f, 1, true, Instant.parse("2025-10-13T12:00:00Z"))) {
            w.writeRow("b", 1);
            assertThrows(IllegalArgumentException.class, () -> w.writeRow("a", 1)); // "a" <= "b"
        }
    }

    @Test
    void readerRejectsNonAscendingWhenSortedTrue() throws Exception {
        Path f = tmp.resolve("bad-sorted-read.vespa_significance_tsv");

        // Build a file by hand to bypass writer check:
        java.nio.file.Files.writeString(f, """
            #VESPA_SIGNIFICANCE_TSV\tv1
            document_count\t1
            sorted\ttrue
            created_at\t2025-10-13T12:00:00Z
            --END-HEADER--
            b\t1
            a\t1
            """);

        var ex = assertThrows(IllegalArgumentException.class, () -> {
            try (var r = new VespaSignificanceTsvReader(f)) {
                while (r.next()) {} // triggers order check
            }
        });
        assertTrue(ex.getMessage().contains("Not strictly ascending"));
    }
}
