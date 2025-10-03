// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespasignificance.export;

import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespasignificance.CommandLineOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * Resolves the path of a index.
 * <p>
 * The path to the index might look like this:
 * - $VESPA_HOME/var/db/vespa/search/CLUSTER/n0/documents/SCHEMA/0.ready/index/index.flush.1/
 * <p>
 * There is a possibility of two separate proton instances running on the same host, therefore,
 * we need to take into consideration that the user must choose between several clusters.
 * However, in the common case, trivially, if there is only one cluster, we can choose that one.
 * <p>
 * There might also be more than one document type for a cluster, and the same applies here. If
 * there are multiple, tell the user to choose, else use trivial one.
 * <p>
 * This class does not consider fields, only locates index on a host.
 *
 * @author johsol
 */
public class IndexLocator {

    String VESPA_HOME = Defaults.getDefaults().vespaHome();

    /** Searches known locations for the index directory. */
    public String locateIndexDir(ExportClientParameters params) {
        var dir = Paths.get(VESPA_HOME, "var", "db", "vespa", "search");

        List<Path> clusterDirs;
        try (Stream<Path> s = Files.list(dir)) {
            clusterDirs = s.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("cluster"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (clusterDirs.isEmpty()) {
            System.out.println("[error] No cluster directory found in: " + dir.toString());
            CommandLineOptions.printExportHelp();
            System.exit(1);
        }

        Path clusterDir;
        if (clusterDirs.size() > 1) {
            if (params.clusterName().isEmpty()) {
                System.out.println("[error] Multiple cluster directory found in: " + dir.toString());
                record Cluster(String name, Path path) {}

                List<Cluster> clusters = clusterDirs.stream()
                        .map(p -> {
                            String fileName = p.getFileName().toString();           // e.g., "cluster.basicsearch"
                            String name = fileName.startsWith("cluster.")
                                    ? fileName.substring("cluster.".length())
                                    : fileName;                                     // fallback if not prefixed
                            return new Cluster(name, p);
                        })
                        .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                        .toList();

                int nameW = Math.max("name".length(),
                        clusters.stream().mapToInt(c -> c.name().length()).max().orElse(4));
                int pathW = Math.max("path".length(),
                        clusters.stream().mapToInt(c -> c.path().toString().length()).max().orElse(4));

                java.util.function.BiFunction<String, Integer, String> rpad = (s, w) ->
                        s + " ".repeat(Math.max(0, w - s.length()));

                System.out.println("Clusters found in: " + dir);
                System.out.println(rpad.apply("name", nameW) + "  " + rpad.apply("path", pathW));
                System.out.println("-".repeat(nameW) + "  " + "-".repeat(pathW));
                for (Cluster c : clusters) {
                    System.out.println(rpad.apply(c.name(), nameW) + "  " + c.path());
                }

                System.out.println();
                System.out.println("Use `--cluster <name>` to select one of the above.");
                System.exit(1);
                return "";
            } else {
                clusterDir = Paths.get(dir.toString(), "cluster." + params.clusterName().get());
            }
        } else {
            // only 1 cluster
            clusterDir = clusterDirs.get(0);
        }

        Path documentsDir = Paths.get(clusterDir.toString(), "n0", "documents");

        // Ensure documents dir exists
        if (!Files.isDirectory(documentsDir)) {
            System.out.println("[error] Documents directory not found: " + documentsDir);
            System.exit(1);
        }

        List<Path> schemaDirs;
        try (Stream<Path> s = Files.list(documentsDir)) {
            schemaDirs = s.filter(Files::isDirectory)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (schemaDirs.isEmpty()) {
            System.out.println("[error] No schema directories found in: " + documentsDir);
            System.exit(1);
        }

        if (params.schemaName().isPresent()) {
            String wanted = params.schemaName().get();
            Path chosen = schemaDirs.stream()
                    .filter(p -> p.getFileName().toString().equals(wanted))
                    .findFirst()
                    .orElse(null);
            if (chosen == null) {
                // Print nice table of available schemas
                record Schema(String name, Path path) {}
                List<Schema> schemas = schemaDirs.stream()
                        .map(p -> new Schema(p.getFileName().toString(), p))
                        .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                        .toList();

                int nameW = Math.max("schema".length(),
                        schemas.stream().mapToInt(s -> s.name().length()).max().orElse(6));
                int pathW = Math.max("path".length(),
                        schemas.stream().mapToInt(s -> s.path().toString().length()).max().orElse(4));
                java.util.function.BiFunction<String,Integer,String> rpad =
                        (str, w) -> str + " ".repeat(Math.max(0, w - str.length()));

                System.out.println("[error] Schema '" + wanted + "' not found under: " + documentsDir);
                System.out.println("Schemas available:");
                System.out.println(rpad.apply("schema", nameW) + "  " + rpad.apply("path", pathW));
                System.out.println("-".repeat(nameW) + "  " + "-".repeat(pathW));
                for (Schema s : schemas) {
                    System.out.println(rpad.apply(s.name(), nameW) + "  " + s.path());
                }
                System.out.println();
                System.out.println("Use `--schema <name>` to select one of the above.");
                System.exit(1);
            }

            Path indexDir = Paths.get(chosen.toString(), "0.ready", "index", "index.flush.1");
            return indexDir.toString();
        }

        if (schemaDirs.size() == 1) {
            Path only = schemaDirs.get(0);
            Path indexDir = Paths.get(only.toString(), "0.ready", "index", "index.flush.1");
            return indexDir.toString();
        } else {
            record Schema(String name, Path path) {}
            List<Schema> schemas = schemaDirs.stream()
                    .map(p -> new Schema(p.getFileName().toString(), p))
                    .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                    .toList();

            int nameW = Math.max("schema".length(),
                    schemas.stream().mapToInt(s -> s.name().length()).max().orElse(6));
            int pathW = Math.max("path".length(),
                    schemas.stream().mapToInt(s -> s.path().toString().length()).max().orElse(4));
            java.util.function.BiFunction<String,Integer,String> rpad =
                    (str, w) -> str + " ".repeat(Math.max(0, w - str.length()));

            System.out.println("[error] Multiple schema directories found in: " + documentsDir);
            System.out.println("Schemas found:");
            System.out.println(rpad.apply("schema", nameW) + "  " + rpad.apply("path", pathW));
            System.out.println("-".repeat(nameW) + "  " + "-".repeat(pathW));
            for (Schema s : schemas) {
                System.out.println(rpad.apply(s.name(), nameW) + "  " + s.path());
            }
            System.out.println();
            System.out.println("Use `--schema <name>` to select one of the above.");
            System.exit(1);
        }

        throw new IllegalStateException("Schema selection fell through unexpectedly");
    }

}
