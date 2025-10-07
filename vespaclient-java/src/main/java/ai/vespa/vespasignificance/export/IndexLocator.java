// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.export;

import com.yahoo.vespa.defaults.Defaults;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Resolves the path of an index.
 * <p>
 * <p>Expected layout:</p>
 * <pre>
 * $VESPA_HOME/var/db/vespa/search/CLUSTER/NODE_INDEX/documents/SCHEMA/0.ready/index/index.flush.1/
 * </pre>
 * Fields are subdirectories inside {@code index.flush.1}.
 * <p>
 * Although uncommon, a host may run multiple clusters and/or multiple node indices.
 * This class uses {@link PathSelector} to disambiguate:
 * <ul>
 *   <li>if a preferred name is provided and exists, it is chosen;</li>
 *   <li>if no preference is provided and there is exactly one candidate, it is chosen;</li>
 *   <li>otherwise a {@link SelectionException} is thrown with options to present to the user.</li>
 * </ul>
 *
 * @author johsol
 */
public class IndexLocator {

    private final Path vespaHome;

    public IndexLocator() {
        this(Path.of(Defaults.getDefaults().vespaHome()));
    }

    // for testing
    IndexLocator(Path vespaHome) {
        this.vespaHome = Objects.requireNonNull(vespaHome);
    }

    /**
     * Resolves the full path to the {@code index.flush.1} directory.
     */
    Path locateIndexDir(@Nullable String clusterName, @Nullable String schemaName, @Nullable String nodeIndex) throws NoSuchFileException {
        Path searchRoot = vespaHome.resolve(Path.of("var", "db", "vespa", "search"));

        // Resolve cluster path
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

        // Resolve node path
        List<Path> nodeDirs = listDirs(clusterDir, p -> Files.isDirectory(p) && p.getFileName().toString().matches("n\\d+"));
        var nodeRes = PathSelector.selectOne(
                nodeDirs,
                nodeIndex,
                "node-index",
                clusterDir,
                p -> p.getFileName().toString().substring(1),
                Path::toString
        );
        Path nodeDir = chooseOrThrow(nodeRes, "node-index");

        // Resolve document path
        Path documentsDir = nodeDir.resolve("documents");
        if (!Files.isDirectory(documentsDir)) {
            throw new NoSuchFileException("Documents directory does not exist: " + documentsDir);
        }

        // Resolve schema path
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

        // Resolve the full path to the index
        Path indexDir = schemaDir.resolve("0.ready").resolve("index");
        if (!Files.exists(indexDir)) {
            throw new NoSuchFileException("Index directory does not exist: " + indexDir);
        }

        // TODO(johsol): WIP
        // triggerFlush creates index.flush.n
        // after index.flush.n is created, then there might start a fusion job
        // which merges several index.flush.n and index.fusion.m into
        // index.fusion.m+1.
        //
        // Unsure what is the correct, but currently finds newest fusion,
        // then newest flush.
        //
        // The problem is that this tool can be invoked while flushing.

        List<Path> candidates = listDirs(indexDir, Files::isDirectory);
        if (candidates.isEmpty()) {
            throw new NoSuchFileException("There is no flushed indexes on disk in: " + indexDir);
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        System.err.println("Found " + candidates.size() + " index candidates. Output might be unstable.");

        Comparator<Path> bySeqDesc =
                Comparator.comparingInt(IndexLocator::seq).reversed()
                        .thenComparing(p -> p.getFileName().toString());

        var latestFusion = candidates.stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .map(indexDir::resolve)
                .filter(p -> p.getFileName().toString().contains("fusion"))
                .max(bySeqDesc);

        if (latestFusion.isPresent()) return latestFusion.get();

        var latestFlush = candidates.stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .map(indexDir::resolve)
                .filter(p -> p.getFileName().toString().contains("flush"))
                .max(bySeqDesc);

        if (latestFlush.isPresent()) return latestFlush.get();

        // unreachable
        throw new NoSuchFileException("Found no fusion/flush directories ending with .<number> under: " + indexDir);
    }

    /**
     * Returns the selected value when {@link PathSelector.Outcome#CHOSEN}, otherwise throws a
     * {@link SelectionException} that includes the outcome, the kind (e.g. {@code "cluster"}),
     * and the list of candidate rows to display.
     */
    private static <T> T chooseOrThrow(PathSelector.Result<T> res, String kind) {
        if (res.outcome() == PathSelector.Outcome.CHOSEN) return res.value();
        throw new SelectionException(res.outcome(), kind, res.message(), res.options());
    }

    /**
     * Lists immediate subdirectories of {@code root} that satisfy {@code filter}, in sorted order.
     */
    private static List<Path> listDirs(Path root, Predicate<Path> filter) throws NoSuchFileException {
        if (!Files.isDirectory(root)) throw new NoSuchFileException("Not a directory: " + root);
        try (Stream<Path> s = Files.list(root)) {
            return s.filter(Files::isDirectory).filter(filter).sorted().toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list: " + root, e);
        }
    }

    private static final Pattern TRAILING_NUM = Pattern.compile(".*\\.(\\d+)$");

    private static int seq(Path p) {
        String name = p.getFileName().toString();
        Matcher m = TRAILING_NUM.matcher(name);
        return m.matches() ? Integer.parseInt(m.group(1)) : -1;
    }

}
