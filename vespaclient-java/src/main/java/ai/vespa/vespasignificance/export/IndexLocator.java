// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.export;

import com.yahoo.vespa.defaults.Defaults;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
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
    Path locateIndexDir(@Nullable String clusterName, @Nullable String schemaName) throws NoSuchFileException {
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
        Path clusterDir = chooseOrThrow(clusterRes, "cluster");

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
        Path schemaDir = chooseOrThrow(schemaRes, "schema");

        Path indexDir = schemaDir.resolve("0.ready").resolve("index").resolve("index.flush.1");
        if (!Files.exists(indexDir)) {
            throw new NoSuchFileException("Index directory does not exist: " + indexDir);
        }

        return indexDir;
    }

    private static <T> T chooseOrThrow(PathSelector.Result<T> res, String kind) {
        if (res.outcome() == PathSelector.Outcome.CHOSEN) return res.value();
        throw new SelectionException(res.outcome(), kind, res.message(), res.options());
    }

    private static List<Path> listDirs(Path root, java.util.function.Predicate<Path> filter) {
        try (Stream<Path> s = Files.list(root)) {
            return s.filter(Files::isDirectory).filter(filter).sorted().toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list: " + root, e);
        }
    }

}
