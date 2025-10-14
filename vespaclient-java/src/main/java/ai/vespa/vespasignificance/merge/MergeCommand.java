// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.merge;

import ai.vespa.vespasignificance.common.VespaSignificanceTsvReader;
import ai.vespa.vespasignificance.common.VespaSignificanceTsvWriter;
import com.yahoo.vespasignificance.CommandLineOptions;
import io.airlift.compress.zstd.ZstdInputStream;
import io.airlift.compress.zstd.ZstdOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * vespa-significance subcommand that merges multiple sorted Vespa Significance TSV inputs into a single VSTSV output.
 *
 * @author johsol
 */
public class MergeCommand {

    private final MergeClientParameters params;
    private Path outputPath;
    private final List<Path> inputPaths = new ArrayList<>();

    public MergeCommand(MergeClientParameters params) {
        this.params = params;
    }

    /**
     * Main entry point for merge subcommand.
     * <p>
     * Returns 0 on success and 1 on failure.
     */
    public int run() {
        try {
            prepareOutputPath();
            resolveAndValidateInputs();
            ensureOutputNotInInputs();

            long totalDocCount = 0L;
            for (Path p : inputPaths) {
                var h = readHeader(p);
                totalDocCount += h.documentCount();
            }

            var merger = new TermDfExternalMerger(
                    new HalfSystemLimitBudget(),
                    inputPaths,
                    MergeCommand::openInputReader
            );

            var output = Files.newOutputStream(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            var bos = new BufferedOutputStream(output, 1 << 16);
            var os = params.zstCompress() ? new ZstdOutputStream(bos) : bos;
            var ow = new OutputStreamWriter(os, StandardCharsets.UTF_8);

            try (var vstsv = new VespaSignificanceTsvWriter(ow, totalDocCount, true, Instant.now())) {
                merger.mergeFiles(vstsv::writeRow, params.minKeep());
                vstsv.flush();
            }

            System.out.println("Merged files to " + outputPath);
        } catch (MergeFailure ignored) {
            return 1;
        } catch (IOException e) {
            System.err.println("I/O error while merging: " + e.getMessage());
            return 1;
        }

        return 0;
    }

    /**
     * Ensures that output file is not null or blank and that we can create parent directories for
     * output. If the output should be zst compressed we add .zst extension if not present.
     */
    private void prepareOutputPath() {
        var out = params.outputFile();
        if (out == null || out.isBlank()) {
            out = "merged.vstsv";
        }

        boolean wantsZst = params.zstCompress();
        boolean hasZstSuffix = out.endsWith(".zst");
        if (wantsZst && !hasZstSuffix) {
            out = out + ".zst";
        }
        outputPath = Path.of(out);

        Path parent = outputPath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                System.err.println("Error: Unable to create parent directory for output: " + parent);
                throw new MergeFailure();
            }
        }
    }

    /**
     * Resolves input strings to {@link Path}s and validates existence.
     */
    private void resolveAndValidateInputs() {
        if (params.inputFiles().isEmpty()) {
            System.err.println("Error: No input files specified.");
            CommandLineOptions.printMergeHelp();
            throw new MergeFailure();
        }

        for (var input : params.inputFiles()) {
            Path path = Path.of(input);
            if (!Files.exists(path)) {
                System.err.println("Error: Input file " + input + " does not exist.");
                throw new MergeFailure();
            }
            inputPaths.add(path);
        }
    }

    /**
     * Check that we do not stream from output to one of the inputs.
     */
    private void ensureOutputNotInInputs() {
        if (outputPath != null && inputPaths.stream()
                .map(p -> p.toAbsolutePath().normalize())
                .anyMatch(p -> p.equals(outputPath.toAbsolutePath().normalize()))) {
            System.err.println("Error: Output file must be different from all input files: " + outputPath);
            throw new MergeFailure();
        }
    }

    /**
     * Opens a reader and skips the header to get to vstsv data section.
     */
    private static BufferedReader openInputReader(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        var in = Files.newInputStream(path);
        var bin = new BufferedInputStream(in, 1 << 16);
        InputStream decoded = name.endsWith(".zst")
                ? new ZstdInputStream(bin)
                : bin;

        var br = new BufferedReader(new InputStreamReader(decoded, StandardCharsets.UTF_8), 1 << 16);
        String line;
        while ((line = br.readLine()) != null) {
            if (Objects.equals(line, VespaSignificanceTsvReader.HEADER_END)) break;
        }
        return br;
    }

    /**
     * Extracts header from vespa significance tsv file (see {@link VespaSignificanceTsvReader}).
     */
    private VespaSignificanceTsvReader.Header readHeader(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        var in = Files.newInputStream(path);
        var bin = new BufferedInputStream(in);
        InputStream decoded = name.endsWith(".zst")
                ? new ZstdInputStream(bin)
                : bin;

        try (var r = new VespaSignificanceTsvReader(new InputStreamReader(decoded, StandardCharsets.UTF_8))) {
            return r.header();
        }
    }

    /**
     * Opens a buffered {@link BufferedWriter} for the output path.
     */
    private static BufferedWriter newOutputWriter(Path output, boolean zst) throws IOException {
        var out = Files.newOutputStream(output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        if (zst) {
            return new BufferedWriter(new OutputStreamWriter(new ZstdOutputStream(new BufferedOutputStream(out)), StandardCharsets.UTF_8));
        } else {
            return new BufferedWriter(new OutputStreamWriter(new BufferedOutputStream(out), StandardCharsets.UTF_8));
        }
    }


    static final class MergeFailure extends RuntimeException {
    }
}
