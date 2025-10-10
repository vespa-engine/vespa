// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.merge;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests methods in {@link TermDfExternalMerger}.
 *
 * @author johsol
 */
public class TermDfExternalMergerTest {

    /**
     * Tracks if the buffered reader was closed.
     */
    static class TrackingBufferedReader extends BufferedReader {
        private volatile boolean closed = false;
        TrackingBufferedReader(Path p) throws IOException {
            super(Files.newBufferedReader(p, StandardCharsets.UTF_8));
        }
        @Override public void close() throws IOException {
            super.close();
            closed = true;
        }
        boolean isClosed() { return closed; }
    }

    /**
     * Writes lines to temporary file, closes the handle and returns the path.
     */
    private static Path writeInput(String... lines) throws IOException {
        Path f = Files.createTempFile("termdf-test-", ".tvl");
        try (BufferedWriter w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                w.write(line);
                w.write('\n');
            }
        }
        return f;
    }

    /**
     * Reads all lines from the writer.
     */
    private static List<String> readAllLines(WriterAndPath out) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(out.path(), StandardCharsets.UTF_8)) {
            List<String> lines = new ArrayList<>();
            String s;
            while ((s = r.readLine()) != null) lines.add(s);
            return lines;
        }
    }

    private record WriterAndPath(BufferedWriter writer, Path path) {}

    private static WriterAndPath newOutputFile() throws IOException {
        Path out = Files.createTempFile("termdf-out-", ".tvl");
        BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8);
        return new WriterAndPath(w, out);
    }

    private static TermDfExternalMerger.BufferedReaderFactory factoryWithTracking(List<TrackingBufferedReader> sink) {
        return path -> {
            TrackingBufferedReader t = new TrackingBufferedReader(path);
            sink.add(t);
            return t;
        };
    }

    // ---------- Tests ----------

    @Test
    void budgetGreaterThanNumberOfFilesSuccess() throws Exception {
        Path f1 = writeInput("a\t1", "c\t1");
        Path f2 = writeInput("a\t2", "b\t1");
        List<Path> files = List.of(f1, f2);

        // Budget: maxReadersAllowed - 1 >= files.size() → single-pass
        FileHandleBudget budget = () -> 1 + files.size(); // writer + all readers

        var readers = new ArrayList<TrackingBufferedReader>();
        var merger = new TermDfExternalMerger(budget, files, factoryWithTracking(readers));

        WriterAndPath out = newOutputFile();
        try (var writer = out.writer()) {
            merger.mergeFiles(writer, /*minKeep*/ 1);
        }

        List<String> lines = readAllLines(out);
        assertEquals(List.of("a\t3", "b\t1", "c\t1"), lines);

        // All readers should be closed
        assertEquals(files.size(), readers.size());
        assertTrue(readers.stream().allMatch(TrackingBufferedReader::isClosed));
    }

    @Test
    void budgetLessThanNumberOfFilesSuccess() throws Exception {
        // 5 inputs, budget allows only 2 readers at a time → batching required
        Path f1 = writeInput("a\t1", "b\t1");
        Path f2 = writeInput("a\t1", "c\t1");
        Path f3 = writeInput("b\t2");
        Path f4 = writeInput("a\t3");
        Path f5 = writeInput("d\t1");
        List<Path> files = List.of(f1, f2, f3, f4, f5);

        FileHandleBudget budget = () -> 3; // writer + 2 readers → batching

        var readers = new ArrayList<TrackingBufferedReader>();
        var merger = new TermDfExternalMerger(budget, files, factoryWithTracking(readers));

        WriterAndPath out = newOutputFile();
        try (var writer = out.writer()) {
            merger.mergeFiles(writer, /*minKeep*/ 1);
        }

        List<String> lines = readAllLines(out);
        // Totals: a=1+1+3=5, b=1+2=3, c=1, d=1
        assertEquals(List.of("a\t5", "b\t3", "c\t1", "d\t1"), lines);

        // All original readers eventually closed
        assertEquals(files.size(), readers.size());
        assertTrue(readers.stream().allMatch(TrackingBufferedReader::isClosed));
    }

    @Test
    void appliesMinKeepSuccess() throws Exception {
        // Set up so that per-batch counts are below minKeep but global total meets minKeep.
        // With budget=3 -> 2 readers per batch. Arrange 'x' split across files.
        Path f1 = writeInput("x\t2");  // batch 1 (alone) => 2
        Path f2 = writeInput("x\t2");  // batch 1 => sum 4
        Path f3 = writeInput("x\t2");  // later merged => final 6

        List<Path> files = List.of(f1, f2, f3);
        FileHandleBudget budget = () -> 3; // 2 readers per batch → (f1,f2) then merge with f3

        var readers = new ArrayList<TrackingBufferedReader>();
        var merger = new TermDfExternalMerger(budget, files, factoryWithTracking(readers));

        WriterAndPath out = newOutputFile();
        try (var writer = out.writer()) {
            // Choose minKeep=5. If intermediate filtering were applied, 'x' would be dropped early.
            merger.mergeFiles(writer, /*minKeep*/ 5);
        }

        List<String> lines = readAllLines(out);
        assertEquals(List.of("x\t6"), lines); // kept only in final pass

        assertTrue(readers.stream().allMatch(TrackingBufferedReader::isClosed));
    }

    @Test
    void infinityBudgetSuccess() throws Exception {
        Path f1 = writeInput("a\t1");
        Path f2 = writeInput("b\t2");
        List<Path> files = List.of(f1, f2);

        FileHandleBudget budget = () -> Long.MAX_VALUE; // effectively infinite

        var readers = new ArrayList<TrackingBufferedReader>();
        var merger = new TermDfExternalMerger(budget, files, factoryWithTracking(readers));

        WriterAndPath out = newOutputFile();
        try (var writer = out.writer()) {
            merger.mergeFiles(writer, 1);
        }

        List<String> lines = readAllLines(out);
        assertEquals(List.of("a\t1", "b\t2"), lines);
        assertTrue(readers.stream().allMatch(TrackingBufferedReader::isClosed));
    }

    @Test
    void emptyInputsSuccess() throws Exception {
        List<Path> files = List.of();
        FileHandleBudget budget = () -> 10;

        var readers = new ArrayList<TrackingBufferedReader>();
        var merger = new TermDfExternalMerger(budget, files, factoryWithTracking(readers));

        WriterAndPath out = newOutputFile();
        try (var writer = out.writer()) {
            merger.mergeFiles(writer, 1);
        }

        List<String> lines = readAllLines(out);
        assertTrue(lines.isEmpty());
        assertTrue(readers.isEmpty()); // no readers opened
    }

    @Test
    void invalidBudgetThrows() {
        List<Path> files = List.of(Path.of("")); // must be non-empty.
        FileHandleBudget bad = () -> 2; // < 3

        var merger = new TermDfExternalMerger(bad, files, p -> Files.newBufferedReader(p, StandardCharsets.UTF_8));

        // Need a writer to invoke mergeFiles
        assertThrows(IllegalArgumentException.class, () -> {
            Path out = Files.createTempFile("termdf-out-", ".tvl");
            try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                merger.mergeFiles(w, 1);
            }
        });
    }

}
