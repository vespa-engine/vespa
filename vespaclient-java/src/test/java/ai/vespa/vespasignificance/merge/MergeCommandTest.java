// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.merge;

import io.airlift.compress.zstd.ZstdInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for MergeCommand.
 *
 * @author johsol
 */
class MergeCommandTest {

    @TempDir Path tmp;

    private static Path writeUtf8(Path p, List<String> lines) throws IOException {
        Files.createDirectories(p.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            for (String s : lines) { w.write(s); w.newLine(); }
        }
        return p;
    }

    private static List<String> readUtf8(Path p) throws IOException {
        return Files.readAllLines(p, StandardCharsets.UTF_8);
    }

    private static List<String> readZstUtf8(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p);
             BufferedInputStream bin = new BufferedInputStream(in);
             ZstdInputStream zst = new ZstdInputStream(bin);
             BufferedReader r = new BufferedReader(new InputStreamReader(zst, StandardCharsets.UTF_8))) {
            return r.lines().toList();
        }
    }

    private static MergeClientParameters params(
            String out, boolean zst, long minKeep, List<String> inputs) {
        return MergeClientParameters.builder()
                .addInputFiles(inputs)
                .outputFile(out)
                .zstCompress(zst)
                .minKeep(minKeep)
                .build();
    }

    private static Path writeVstsv(Path p, long documentCount, String createdAtIsoUtc, List<String> dataLines) throws IOException {
        Files.createDirectories(p.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            w.write("#VESPA_SIGNIFICANCE_TSV\tv1\n");
            w.write("document_count\t" + documentCount + "\n");
            w.write("sorted\ttrue\n");
            w.write("created_at\t" + createdAtIsoUtc + "\n");
            w.write("--END-HEADER--\n");
            for (String s : dataLines) { w.write(s); w.newLine(); }
        }
        return p;
    }

    private static Path writeVstsvZst(Path p, List<String> dataLines) throws IOException {
        Files.createDirectories(p.getParent());
        try (OutputStream raw = Files.newOutputStream(p);
             var zwo = new io.airlift.compress.zstd.ZstdOutputStream(new BufferedOutputStream(raw));
             var w = new BufferedWriter(new OutputStreamWriter(zwo, StandardCharsets.UTF_8))) {
            w.write("#VESPA_SIGNIFICANCE_TSV\tv1\n");
            w.write("document_count\t2\n");
            w.write("sorted\ttrue\n");
            w.write("created_at\t2025-10-14T07:56:27Z\n");
            w.write("--END-HEADER--\n");
            for (String s : dataLines) { w.write(s); w.newLine(); }
        }
        return p;
    }

    @Test
    void mergesVstsvFiles() throws Exception {
        Path in1 = writeVstsv(tmp.resolve("in1.vstsv"), 1, "2025-10-14T07:56:26Z", List.of("a\t1", "c\t1"));
        Path in2 = writeVstsv(tmp.resolve("in2.vstsv"), 2, "2025-10-14T07:56:27Z", List.of("a\t2", "b\t3"));
        Path out = tmp.resolve("out.vstsv");

        MergeCommand cmd = new MergeCommand(params(out.toString(), false, 1, List.of(in1.toString(), in2.toString())));
        int rc = cmd.run();
        assertEquals(0, rc);

        var lines = readUtf8(out);
        // Header assertions (created_at is dynamic; check shape)
        assertEquals("#VESPA_SIGNIFICANCE_TSV\tv1", lines.get(0));
        assertTrue(lines.get(1).startsWith("document_count\t")); // should be 1 + 2 = 3
        assertEquals("sorted\ttrue", lines.get(2));
        assertTrue(lines.get(3).startsWith("created_at\t"));
        assertEquals("--END-HEADER--", lines.get(4));

        // Data
        assertEquals(List.of("a\t3", "b\t3", "c\t1"), lines.subList(5, lines.size()));
    }

    @Test
    void mergesCombinationOfCompressedAndUncompressed() throws Exception {
        Path inPlain = writeVstsv(tmp.resolve("in-plain.vstsv"), 1, "2025-10-14T07:56:26Z", List.of("x\t1", "z\t1"));
        Path inZst = writeVstsvZst(tmp.resolve("in.vstsv.zst"), List.of("x\t2", "y\t5"));

        Path outNoSuffix = tmp.resolve("final"); // user omitted .zst
        MergeCommand cmd = new MergeCommand(params(outNoSuffix.toString(), true, 1,
                List.of(inPlain.toString(), inZst.toString())));
        int rc = cmd.run();
        assertEquals(0, rc);

        Path out = Path.of(outNoSuffix + ".zst");
        assertTrue(Files.exists(out), "expected .zst to be appended");

        var lines = readZstUtf8(out);
        assertEquals("#VESPA_SIGNIFICANCE_TSV\tv1", lines.get(0));
        assertTrue(lines.get(1).startsWith("document_count\t")); // 1 + 2 = 3
        assertEquals("sorted\ttrue", lines.get(2));
        assertTrue(lines.get(3).startsWith("created_at\t"));
        assertEquals("--END-HEADER--", lines.get(4));

        assertEquals(List.of("x\t3", "y\t5", "z\t1"), lines.subList(5, lines.size()));
    }

    @Test
    void appliesMinKeep() throws Exception {
        Path f1 = writeVstsv(tmp.resolve("f1.vstsv"), 1, "2025-10-14T07:56:26Z", List.of("t\t2"));
        Path f2 = writeVstsv(tmp.resolve("f2.vstsv"), 1, "2025-10-14T07:56:27Z", List.of("t\t2"));
        Path f3 = writeVstsv(tmp.resolve("f3.vstsv"), 1, "2025-10-14T07:56:28Z", List.of("t\t2"));
        Path out = tmp.resolve("out.vstsv");

        MergeCommand cmd = new MergeCommand(params(out.toString(), false, /*minKeep*/5,
                List.of(f1.toString(), f2.toString(), f3.toString())));
        int rc = cmd.run();
        assertEquals(0, rc);

        var lines = readUtf8(out);
        assertEquals("#VESPA_SIGNIFICANCE_TSV\tv1", lines.get(0));
        assertTrue(lines.get(1).startsWith("document_count\t")); // 1+1+1=3
        assertEquals("sorted\ttrue", lines.get(2));
        assertTrue(lines.get(3).startsWith("created_at\t"));
        assertEquals("--END-HEADER--", lines.get(4));
        assertEquals(List.of("t\t6"), lines.subList(5, lines.size()));
    }

    @Test
    void failsWhenOutputMissing() {
        MergeCommand cmd = new MergeCommand(params("", false, 1, List.of("a")));
        assertEquals(1, cmd.run());
    }

    @Test
    void failsWhenNoInputsProvided() {
        Path out = tmp.resolve("out.tsv");
        MergeCommand cmd = new MergeCommand(params(out.toString(), false, 1, List.of()));
        assertEquals(1, cmd.run());
    }

    @Test
    void failsWhenAnyInputMissing() {
        Path out = tmp.resolve("out.tsv");
        Path existing = tmp.resolve("exists.tsv");
        assertDoesNotThrow(() -> writeUtf8(existing, List.of("a\t1")));
        MergeCommand cmd = new MergeCommand(params(out.toString(), false, 1,
                List.of(existing.toString(), tmp.resolve("missing.tsv").toString())));
        assertEquals(1, cmd.run());
    }

    @Test
    void failsWhenOutputEqualsAnInput() throws Exception {
        Path io = writeUtf8(tmp.resolve("same.tsv"), List.of("a\t1"));
        MergeCommand cmd = new MergeCommand(params(io.toString(), false, 1, List.of(io.toString())));
        assertEquals(1, cmd.run());
    }
}

