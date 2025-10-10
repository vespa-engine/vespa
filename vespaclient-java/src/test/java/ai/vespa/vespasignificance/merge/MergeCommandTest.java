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

    @Test
    void mergesTsvFiles() throws Exception {
        Path in1 = writeUtf8(tmp.resolve("in1.tsv"), List.of("a\t1", "c\t1"));
        Path in2 = writeUtf8(tmp.resolve("in2.tsv"), List.of("a\t2", "b\t3"));
        Path out = tmp.resolve("out.tsv");

        MergeCommand cmd = new MergeCommand(params(out.toString(), false, 1, List.of(in1.toString(), in2.toString())));
        int rc = cmd.run();
        assertEquals(0, rc);

        assertEquals(List.of("a\t3", "b\t3", "c\t1"), readUtf8(out));
    }

    @Test
    void mergesCombinationOfCompressedAndUncompressed() throws Exception {
        Path inPlain = writeUtf8(tmp.resolve("in-plain.tsv"), List.of("x\t1", "z\t1"));
        // prepare zst input
        Path inZst = tmp.resolve("in.zst");
        // compress: simplest is to write plain then recompress via MergeCommand’s reader path,
        // but here we write plain and rely on filename for detection. To be strict, write legit zst:
        try (OutputStream raw = Files.newOutputStream(inZst);
             var zwo = new io.airlift.compress.zstd.ZstdOutputStream(new BufferedOutputStream(raw));
             var w = new BufferedWriter(new OutputStreamWriter(zwo, StandardCharsets.UTF_8))) {
            w.write("x\t2\n");
            w.write("y\t5\n");
        }

        Path outNoSuffix = tmp.resolve("final"); // user omitted .zst
        MergeCommand cmd = new MergeCommand(params(outNoSuffix.toString(), true, 1,
                List.of(inPlain.toString(), inZst.toString())));
        int rc = cmd.run();
        assertEquals(0, rc);

        Path out = Path.of(outNoSuffix + ".zst"); // suffix appended
        assertTrue(Files.exists(out), "expected .zst to be appended");

        assertEquals(List.of("x\t3", "y\t5", "z\t1"), readZstUtf8(out));
    }

    @Test
    void appliesMinKeep() throws Exception {
        Path f1 = writeUtf8(tmp.resolve("f1.tsv"), List.of("t\t2"));
        Path f2 = writeUtf8(tmp.resolve("f2.tsv"), List.of("t\t2"));
        Path f3 = writeUtf8(tmp.resolve("f3.tsv"), List.of("t\t2"));
        Path out = tmp.resolve("out.tsv");

        MergeCommand cmd = new MergeCommand(params(out.toString(), false, /*minKeep*/5,
                List.of(f1.toString(), f2.toString(), f3.toString())));
        int rc = cmd.run();
        assertEquals(0, rc);

        assertEquals(List.of("t\t6"), readUtf8(out));
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

