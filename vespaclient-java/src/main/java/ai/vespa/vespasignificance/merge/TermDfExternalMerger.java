// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.merge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

/**
 * External k-way merge of multiple sorted files, respecting a file-handle budget.
 * <p>
 * If the number of inputs exceeds the available reader budget, inputs are merged in
 * intermediate batches into temporary files that this class creates, manages, and cleans up.
 * The final pass writes to the caller-provided {@link BufferedWriter}.
 * <p>
 * Ownership and I/O:
 * <ul>
 *   <li>Caller supplies input paths and a {@link BufferedReader} factory for caller-owned inputs.</li>
 *   <li>Temporary files are created in a private directory and deleted when consumed.</li>
 *   <li>This class closes all {@code BufferedReader}s it opens. The caller remains responsible for the
 *       provided {@code BufferedWriter}.</li>
 * </ul>
 * <p>
 * The {@code minKeep} filter is only applied to the final merge.
 *
 * @author johsol
 */
public class TermDfExternalMerger {

    private static final long NO_MIN_KEEP = Long.MIN_VALUE;

    private final FileHandleBudget fileHandleBudget;
    private final List<Path> files;
    private final BufferedReaderFactory readerFactory;

    public TermDfExternalMerger(FileHandleBudget fileHandleBudget, List<Path> files, BufferedReaderFactory readerFactory) {
        this.fileHandleBudget = Objects.requireNonNull(fileHandleBudget, "fileHandleBudget");
        this.files = List.copyOf(files);
        this.readerFactory = Objects.requireNonNull(readerFactory, "readerFactory");
    }

    /**
     * Provide a function that can open a buffered reader from a path.
     */
    @FunctionalInterface
    public interface BufferedReaderFactory {
        BufferedReader open(Path path) throws IOException;
    }

    /**
     * Classify if this class or the caller should open a file.
     */
    enum SourceType {

        /** The caller should open the file */
        External,

        /** This class should open the file */
        Tempfile

    }

    /**
     * Holds a reference to a file in the queue .
     */
    record MergeSource(SourceType type, Path path) {
    }

    /**
     * Represents a batch and holds a reference to the files that were consumed to have a reference
     * to temporary files so we can delete them after they are consumed.
     */
    private record MergeBatch(List<BufferedReader> readers, List<MergeSource> consumed) {
    }

    /**
     * Merges all configured input files and writes the merged result to {@code sink}.
     * <ul>
     *   <li>If the file-handle budget is greater than the number of inputs, merging is performed in
     *   a single pass.</li>
     *   <li>Otherwise, inputs are merged in batches into temporary files. The final pass merges and writes to the
     *   {@code sink}.</li>
     *   <li>Only the final pass applies {@code minKeep}; intermediate passes use {@link #NO_MIN_KEEP}.</li>
     * </ul>
     * <p>
     * Ownership:
     * <ul>
     *   <li>All {@code BufferedReader}s opened by this class are closed within this method. Including those opened
     *   with the {@link BufferedReaderFactory}.</li>
     * </ul>
     */
    public void mergeFiles(TermDfRowSink sink, long minKeep) throws IOException {
        Objects.requireNonNull(sink, "sink");
        if (files.isEmpty()) return;

        int budget = computeReaderBudget();

        // Fast path
        if (budget >= files.size()) {
            final List<BufferedReader> readers = new ArrayList<>(files.size());
            try {
                for (Path p : files) readers.add(readerFactory.open(p));
                TermDfKWayMerge.merge(readers, sink, minKeep);
            } finally {
                closeQuietly(readers);
            }
            return;
        }

        // Batched path
        final Queue<MergeSource> filesToMerge = new ArrayDeque<>();
        for (Path p : files) {
            filesToMerge.offer(new MergeSource(SourceType.External, p));
        }

        final Path tempDir = Files.createTempDirectory("termdf-");
        try {
            while (filesToMerge.size() > budget) {
                final MergeBatch batch = dequeueBatch(budget, filesToMerge);
                final Path tempFile = Files.createTempFile(tempDir, "termdf-", ".tmp.tsv");

                try (BufferedWriter tmpWriter = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8, StandardOpenOption.WRITE)) {
                    mergeTemporaryToTsv(batch.readers(), tmpWriter);
                } finally {
                    closeQuietly(batch.readers());
                    for (MergeSource e : batch.consumed()) {
                        if (e.type == SourceType.Tempfile) deleteQuietly(e.path);
                    }
                }

                filesToMerge.offer(new MergeSource(SourceType.Tempfile, tempFile));
            }

            // Final pass. Write to output and filter out minKeep.
            final MergeBatch finalBatch = dequeueBatch(budget, filesToMerge);
            try {
                TermDfKWayMerge.merge(finalBatch.readers(), sink, minKeep);
            } finally {
                closeQuietly(finalBatch.readers());
                for (MergeSource e : finalBatch.consumed()) {
                    if (e.type == SourceType.Tempfile) deleteQuietly(e.path);
                }
            }
        } finally {
            deleteTreeQuietly(tempDir);
        }
    }

    /**
     * Used for temps that don't need to write intermediate header.
     */
    static void mergeTemporaryToTsv(List<BufferedReader> inputs, BufferedWriter out) throws IOException {
        TermDfKWayMerge.merge(inputs, (term, df) -> {
            out.write(term);
            out.write('\t');
            out.write(Long.toString(df));
            out.write('\n');
        }, TermDfExternalMerger.NO_MIN_KEEP);
        out.flush();
    }

    // for tests
    void mergeFiles(BufferedWriter output, long minKeep) throws IOException {
        Objects.requireNonNull(output, "output");
        TermDfRowSink sink = (term, df) -> {
            output.write(term);
            output.write('\t');
            output.write(Long.toString(df));
            output.write('\n');
        };
        mergeFiles(sink, minKeep);
        output.flush();
    }

    /**
     * Validates the budget and converts to int.
     */
    private int computeReaderBudget() {
        final long budget = fileHandleBudget.maxReadersAllowed();
        if (budget < 3) throw new IllegalArgumentException("Cannot merge with less than 3 file handles.");
        final long rawReaderBudget = budget - 1;
        final int readerBudget;
        if (rawReaderBudget >= files.size()) {
            readerBudget = files.size();
        } else {
            readerBudget = (int) rawReaderBudget;
        }
        return readerBudget;
    }

    /**
     * Makes a batch with reader budget amount of entries which are popped from the queue.
     */
    private MergeBatch dequeueBatch(int readerBudget, Queue<MergeSource> filesToMerge) throws IOException {
        final List<BufferedReader> readers = new ArrayList<>(readerBudget);
        final List<MergeSource> consumed = new ArrayList<>(readerBudget);

        while (!filesToMerge.isEmpty() && readers.size() < readerBudget) {
            MergeSource entry = filesToMerge.poll();
            consumed.add(entry);
            BufferedReader r = (entry.type == SourceType.Tempfile)
                    ? Files.newBufferedReader(entry.path, StandardCharsets.UTF_8)
                    : readerFactory.open(entry.path);
            readers.add(r);
        }
        return new MergeBatch(readers, consumed);
    }

    private static void closeQuietly(List<BufferedReader> readers) {
        for (BufferedReader r : readers) {
            try {
                if (r != null) r.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    private static void deleteTreeQuietly(Path dir) {
        try (var s = Files.walk(dir)) {
            s.sorted((a, b) -> b.getNameCount() - a.getNameCount()) // delete children first
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

}
