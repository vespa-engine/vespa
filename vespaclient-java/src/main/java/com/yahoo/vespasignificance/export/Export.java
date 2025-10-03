// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespasignificance.export;

import com.yahoo.vespasignificance.CommandLineOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * This class uses vespa-index-inspect to export significance model from
 * flushed indexes to disk.
 *
 * @author johsol
 */
public class Export {

    ExportClientParameters params;
    Path indexDir;


    public Export(ExportClientParameters params) {
        this.params = params;
    }

    public void run() {
        resolveIndexDir();
        requireFieldDir(indexDir, params.fieldName());
        callVespaIndexInspect();
    }

    private void resolveIndexDir() {
        if (params.locateIndex()) {
            System.out.println("Locating index directory");
            indexDir = new IndexLocator().locateIndexDir(params);
        } else {
            if (params.indexDir() == null || params.indexDir().isEmpty()) {
                System.out.println("[error] No index directory specified.");
                System.out.println("Use --index-dir to specify index directory or --locate-index to search for index directory.");
                CommandLineOptions.printExportHelp();
                System.exit(1);
            }

            if (!Paths.get(params.indexDir()).toFile().exists()) {
                System.out.println("[error] Index directory `" + params.indexDir() + "` does not exist.");
                System.exit(1);
            }

            indexDir = Paths.get(params.indexDir());
        }
    }

    private void callVespaIndexInspect() {
        var indexInspect = new VespaIndexInspectClient();
        try {
            var df = indexInspect.dumpWords(indexDir, params.fieldName());
            System.out.println("Extracted values:");
            for (var entry : df.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void requireFieldDir(Path indexDir, String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            System.out.println("[error] No --field specified.");
            System.exit(1);
        }

        // Collect candidate field directories (depth 1 and 2)
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> s1 = Files.list(indexDir)) {
            s1.filter(Files::isDirectory).forEach(candidates::add);
        } catch (IOException e) {
            throw new RuntimeException("Failed to list index directory: " + indexDir, e);
        }

        // Depth 2 (common: index/<something>/<field>)
        List<Path> depth2 = new ArrayList<>();
        for (Path p : candidates) {
            try (Stream<Path> s2 = Files.list(p)) {
                s2.filter(Files::isDirectory).forEach(depth2::add);
            } catch (IOException ignored) {}
        }
        candidates.addAll(depth2);

        // Try exact match on the leaf directory name
        for (Path p : candidates) {
            String leaf = p.getFileName().toString();
            if (leaf.equals(fieldName)) {
                return; // found → OK
            }
        }

        // Not found → show helpful listing of what we DID find
        List<List<String>> rows = candidates.stream()
                .map(p -> List.of(p.getFileName().toString(), p.toString()))
                .sorted((a, b) -> a.get(0).compareToIgnoreCase(b.get(0)))
                .toList();

        if (rows.isEmpty()) {
            System.out.println("[error] No field directories found under: " + indexDir);
        } else {
            TablePrinter.printTable(
                    "[error] Field directory '" + fieldName + "' not found under: " + indexDir,
                    List.of("field", "path"),
                    rows
            );
            System.out.println();
            System.out.println("Use `--field <name>` to select one of the above.");
        }
        System.exit(1);
    }
}
