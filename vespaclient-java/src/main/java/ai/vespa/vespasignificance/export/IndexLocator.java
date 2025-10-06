// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.export;

import com.yahoo.vespa.defaults.Defaults;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * Resolves the path of an index.
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

    private final String VESPA_HOME = Defaults.getDefaults().vespaHome();

    /**
     * Resolves the index dir or exits with a helpful message.
     * <p>
     * On none or more than 1 match, it will exit.
     */
    Path locateIndexDir(@Nullable String clusterName, @Nullable String schemaName) {
        Path searchRoot = Paths.get(VESPA_HOME, "var", "db", "vespa", "search");

        // Not common, but might be more than 1 cluster on one host.
        List<Path> clusterDirs = listDirs(searchRoot, p -> p.getFileName().toString().startsWith("cluster"));
        var clusterRes = PathSelector.selectOne(
                clusterDirs,
                clusterName,
                "cluster",
                searchRoot,
                p -> {
                    String name = p.getFileName().toString();
                    return name.startsWith("cluster.") ? name.substring("cluster.".length()) : name;
                },
                Path::toString
        );
        Path clusterDir = chooseOrExit(clusterRes, "cluster");

        Path documentsDir = clusterDir.resolve("n0").resolve("documents");
        List<Path> schemaDirs = listDirs(documentsDir, Files::isDirectory);
        var schemaRes = PathSelector.selectOne(
                schemaDirs,
                schemaName,
                "schema",
                documentsDir,
                p -> p.getFileName().toString(),
                Path::toString
        );
        Path schemaDir = chooseOrExit(schemaRes, "schema");

        Path indexDir = schemaDir.resolve("0.ready").resolve("index").resolve("index.flush.1");
        if (!Files.exists(indexDir)) {
            System.out.println("Index directory does not exist: " + indexDir);
            System.exit(1);
        }

        return indexDir;
    }

    /** Parse the {@link PathSelector.Result}. */
    private static <T> T chooseOrExit(PathSelector.Result<T> res, String kind) {
        if (res.outcome() == PathSelector.Outcome.CHOSEN) return res.value();

        System.out.println("Error: " + res.message());
        var rows = res.options();
        if (!rows.isEmpty()) {
            TablePrinter.printTable(
                    null,
                    List.of(kind, "path"),
                    rows.stream().map(r -> List.of(r.name(), r.path())).toList()
            );
            System.out.println();
            System.out.println("Use `--" + kind + " <name>` to select one of the above.");
        }

        System.exit(1);
        throw new IllegalStateException("unreachable");
    }

    private static List<Path> listDirs(Path root, java.util.function.Predicate<Path> filter) {
        try (Stream<Path> s = Files.list(root)) {
            return s.filter(Files::isDirectory).filter(filter).sorted().toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list: " + root, e);
        }
    }

}
