// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.merge;

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
import java.util.ArrayList;
import java.util.List;

/**
 * vespa-significance subcommand that merges multiple sorted TSV inputs into a single TSV output.
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

            var merger = new TermDfExternalMerger(new HalfSystemLimitBudget(), inputPaths, MergeCommand::openInputReader);
            try (var writer = newOutputWriter(outputPath, params.zstCompress())) {
                merger.mergeFiles(writer, params.minKeep());
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
            System.err.println("Error: --output is required.");
            CommandLineOptions.printMergeHelp();
            throw new MergeFailure();
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
     * Opens an input path as a buffered {@link BufferedReader}.
     */
    private static BufferedReader openInputReader(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);

        if (name.endsWith(".zst")) {
            InputStream in = Files.newInputStream(path, StandardOpenOption.READ);
            BufferedInputStream bin = new BufferedInputStream(in);
            ZstdInputStream zst = new ZstdInputStream(bin);
            return new BufferedReader(new InputStreamReader(zst, StandardCharsets.UTF_8));
        }

        return Files.newBufferedReader(path, StandardCharsets.UTF_8);
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
