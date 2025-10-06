// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.export;

import com.yahoo.vespasignificance.CommandLineOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * This class uses vespa-index-inspect to read terms and their document frequency and sorts
 * the rows lexicographically on term, then writes the result to disk.
 *
 * @author johsol
 */
public class Export {

    private final ExportClientParameters params;
    private final IndexLocator locator;
    private final WriterFactory writerFactory;
    private final DumpFn dumpFn;

    private Path indexDir;
    private String fieldName;

    public Export(ExportClientParameters params) {
        this(params,
                new IndexLocator(),
                TsvTermDfWriter::new,
                (idx, field) -> new VespaIndexInspectClient().streamDumpWords(idx, field));
    }

    // for tests
    Export(ExportClientParameters params,
           IndexLocator locator,
           WriterFactory writerFactory,
           DumpFn dumpFn) {
        this.params = Objects.requireNonNull(params);
        this.locator = Objects.requireNonNull(locator);
        this.writerFactory = Objects.requireNonNull(writerFactory);
        this.dumpFn = Objects.requireNonNull(dumpFn);
    }

    @FunctionalInterface
    interface WriterFactory {
        TermDfWriter create(Path out) throws IOException;
    }

    @FunctionalInterface
    interface DumpFn {
        Stream<VespaIndexInspectClient.TermDocumentFrequency> open(Path indexDir, String field) throws IOException;
    }

    /**
     * Main entry point for export subcommand.
     * <p>
     * Return 0 on success and 1 on failure.
     */
    public int run() {
        try {
            ensureOutputFile();
            resolveIndexDir();
            requireFieldDir(params.fieldName());

            Path outFile = Path.of(params.outputFile());
            var ordering = Comparator.comparing(VespaIndexInspectClient.TermDocumentFrequency::term);
            try (var rows = dumpFn.open(indexDir, fieldName).sorted(ordering);
                 TermDfWriter writer = writerFactory.create(outFile)) {
                writer.writeAll(rows);
            }

            System.out.println("Exported " + Path.of(indexDir.toString(), fieldName) + " to " + outFile);
            return 0;
        } catch (ExportFailure ignored) {
            return 1;
        } catch (IOException e) {
            System.err.println("Error during export: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Ensures that output file is not null or blank and that we can create parent directories for
     * output.
     */
    private void ensureOutputFile() {
        if (params.outputFile() == null || params.outputFile().isBlank()) {
            System.err.println("Error: --output is required.");
            CommandLineOptions.printExportHelp();
            throw new ExportFailure();
        }

        Path outFile = Path.of(params.outputFile());
        Path parent = outFile.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                System.err.println("Error: Unable to create parent directory for output: " + parent);
                throw new ExportFailure();
            }
        }
    }

    /**
     * Resolves the index dir to read data from. If index directory is not provided, then we try to search
     * for it using {@link IndexLocator}. If this procedure does not fail, the {@link Export#indexDir} field will
     * be set to the index dir to search from.
     */
    private void resolveIndexDir() {
        if (params.indexDir() == null) {
            try {
                indexDir = locator.locateIndexDir(params.clusterName().orElse(null),
                        params.schemaName().orElse(null),
                        params.nodeIndex().orElse(null));
            } catch (SelectionException e) {
                handleSelectionException(e);
            } catch (NoSuchFileException nf) {
                System.err.println(nf.getMessage());
                throw new ExportFailure();
            }
        } else {
            if (params.indexDir().isEmpty()) {
                System.err.println("Error: No index directory specified.");
                System.err.println("Use --index-dir to specify index directory or --locate-index to search for index directory.");
                CommandLineOptions.printExportHelp();
                throw new ExportFailure();
            }

            Path explicit = Path.of(params.indexDir());
            if (!Files.isDirectory(explicit)) {
                System.err.println("Error: Index directory `" + explicit + "` does not exist or is not a directory.");
                throw new ExportFailure();
            }
            indexDir = explicit;
        }
    }

    /**
     * When the index directory is resolved, we can check that the field name is a subdirectory in the index
     * directory. If the directory is found, then the {@link Export#fieldName} is set.
     */
    private void requireFieldDir(String fieldName) {
        if (!Files.isDirectory(indexDir)) {
            System.err.println("Error: Index directory `" + indexDir + "` does not exist or is not a directory.");
            throw new ExportFailure();
        }
        List<Path> fieldDirs;
        try (Stream<Path> s = Files.list(indexDir)) {
            fieldDirs = s.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            System.err.println("Failed to list index directory: " + indexDir + " (" + e.getMessage() + ")");
            throw new ExportFailure();
        }
        var res = PathSelector.selectOne(fieldDirs, fieldName, "field", indexDir,
                p -> p.getFileName().toString(), Path::toString);
        if (res.outcome() == PathSelector.Outcome.CHOSEN) {
            this.fieldName = Objects.requireNonNull(res.value()).getFileName().toString();
            return;
        }
        handleSelectionException(new SelectionException(res.outcome(), "field", res.message(), res.options()));
    }

    private void handleSelectionException(SelectionException e) {
        System.err.println("Error: " + e.getMessage());
        var rows = e.options();
        if (!rows.isEmpty()) {
            TablePrinter.printTable(
                    System.err,
                    null,
                    List.of(e.kind(), "path"),
                    rows.stream().map(r -> List.of(r.name(), r.path())).toList()
            );
            System.err.println();
            System.err.println("Use `--" + e.kind() + " <name>` to select one of the above.");
        }
        throw new ExportFailure();
    }

    static final class ExportFailure extends RuntimeException {
    }
}
