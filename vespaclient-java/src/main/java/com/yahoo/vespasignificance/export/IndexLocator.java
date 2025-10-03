// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespasignificance.export;

import com.yahoo.vespa.defaults.Defaults;

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

    /**
     * Resolves the index dir or exits with a helpful message.
     * <p>
     * On none or more than 1 match, it will exit.
     */
    Path locateIndexDir(ExportClientParameters params) {
        Path searchRoot = Paths.get(VESPA_HOME, "var", "db", "vespa", "search");

        // Not common, but might be more than 1 cluster on one host.
        List<Path> clusterDirs = listDirs(searchRoot, p -> p.getFileName().toString().startsWith("cluster"));
        var clusterSel = Selector.selectOneOrExplain(
                clusterDirs,
                params.clusterName().orElse(null),
                "cluster",
                searchRoot,
                p -> {
                    String n = p.getFileName().toString();
                    return n.startsWith("cluster.") ? n.substring("cluster.".length()) : n;
                },
                Path::toString
        );
        if (clusterSel.shouldExit()) System.exit(1);
        Path clusterDir = clusterSel.getValue();

        // There might be more than 1 schema in a cluster
        Path documentsDir = clusterDir.resolve("n0").resolve("documents");
        List<Path> schemaDirs = listDirs(documentsDir, Files::isDirectory);
        var schemaSel = Selector.selectOneOrExplain(
                schemaDirs,
                params.schemaName().orElse(null),
                "schema",
                documentsDir,
                p -> p.getFileName().toString(),
                Path::toString
        );
        if (schemaSel.shouldExit()) System.exit(1);
        Path schemaDir = schemaSel.getValue();

        Path indexDir = schemaDir.resolve("0.ready").resolve("index").resolve("index.flush.1");
        if (!Files.exists(indexDir)) {
            System.out.println("Index directory does not exist: " + indexDir);
            System.exit(1);
        }

        return indexDir;
    }

    private static List<Path> listDirs(Path root, java.util.function.Predicate<Path> filter) {
        try (Stream<Path> s = Files.list(root)) {
            return s.filter(Files::isDirectory).filter(filter).sorted().toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list: " + root, e);
        }
    }

}
