// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.vespasignificance.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testing {@link TsvTermDfWriter}.
 *
 * @author johsol
 */
class TsvTermDfWriterTest {

    @TempDir
    Path tmp;

    private TsvTermDfWriter newWriter(Path out) throws IOException {
        Writer w = new OutputStreamWriter(Files.newOutputStream(out), StandardCharsets.UTF_8);
        return new TsvTermDfWriter(w);
    }

    @Test
    void writesUtf8TsvAndNewlines() throws Exception {
        Path out = tmp.resolve("out.tsv");
        try (var w = newWriter(out)) {
            w.write(new VespaIndexInspectClient.TermDocumentFrequency("hello", 1));
            w.write(new VespaIndexInspectClient.TermDocumentFrequency("world", 2));
            w.flush();
        }
        var text = Files.readString(out, StandardCharsets.UTF_8);
        assertEquals("hello\t1\nworld\t2\n", text);
    }

    @Test
    void writeAllConsumesStreamAndClosesIt() throws Exception {
        Path out = tmp.resolve("out.tsv");
        boolean[] closed = {false};
        Stream<VespaIndexInspectClient.TermDocumentFrequency> rows =
                Stream.of(
                        new VespaIndexInspectClient.TermDocumentFrequency("a", 1),
                        new VespaIndexInspectClient.TermDocumentFrequency("b", 2)
                ).onClose(() -> closed[0] = true);

        try (var w = newWriter(out)) {
            w.writeAll(rows); // should consume and close the stream
        }
        assertTrue(closed[0], "writeAll should close the input stream");

        var lines = Files.readAllLines(out, StandardCharsets.UTF_8);
        assertEquals(List.of("a\t1", "b\t2"), lines);
    }

    @Test
    void cannotWriteAfterClose() throws Exception {
        Path out = tmp.resolve("out.tsv");
        var w = newWriter(out);
        w.close();
        assertThrows(IOException.class,
                () -> w.write(new VespaIndexInspectClient.TermDocumentFrequency("x", 1)));
    }

    @Test
    void largeInputOrderIsCallerControlled() throws Exception {
        Path out = tmp.resolve("out.tsv");
        var rows = Stream.of(
                new VespaIndexInspectClient.TermDocumentFrequency("c", 3),
                new VespaIndexInspectClient.TermDocumentFrequency("a", 1),
                new VespaIndexInspectClient.TermDocumentFrequency("b", 2)
        ).sorted(Comparator.comparing(VespaIndexInspectClient.TermDocumentFrequency::term));

        try (var w = newWriter(out)) {
            w.writeAll(rows);
        }
        var text = Files.readString(out, StandardCharsets.UTF_8);
        assertEquals("a\t1\nb\t2\nc\t3\n", text);
    }
}
