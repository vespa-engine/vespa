// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test path resolving logic in {@link IndexLocator}.
 *
 * @author johsol
 */
class IndexLocatorTest {

    @TempDir
    Path tmp;

    private Path searchRoot() { return tmp.resolve("var/db/vespa/search"); }

    private Path mkIndexTree(String cluster, String node, String schema, boolean withIndex) throws Exception {
        Path base = searchRoot().resolve(cluster).resolve(node).resolve("documents").resolve(schema);
        Path flush = base.resolve("0.ready/index/index.flush.1");
        Files.createDirectories(withIndex ? flush : base.resolve("0.ready/index"));
        return flush;
    }

    /**
     * The user provides trough CLI cluster name, node index and schema name.
     */
    @Test
    void resolvesWhenArgsProvided() throws Exception {
        Path expected = mkIndexTree("cluster.alpha", "n0", "doc", true);
        IndexLocator locator = new IndexLocator(tmp);

        Path actual = locator.locateIndexDir("alpha", "doc", "n0");

        assertEquals(expected, actual);
    }

    /**
     * The user does not provide any flags, but the path is trivial so it resolves
     * anyway.
     */
    @Test
    void resolvesWhenUnambiguousAndNoArgsProvided() throws Exception {
        Path expected = mkIndexTree("cluster.alpha", "n0", "doc", true);
        IndexLocator locator = new IndexLocator(tmp);

        Path actual = locator.locateIndexDir(null, null, null);

        assertEquals(expected, actual);
    }

    /**
     * If user wanted beta, but only alpha existed. Ensures that user preference has precedence.
     */
    @Test
    void preferredClusterNotFoundThrowsSelectionException() throws Exception {
        mkIndexTree("cluster.alpha", "n0", "doc", true);
        IndexLocator locator = new IndexLocator(tmp);

        SelectionException e = assertThrows(
                SelectionException.class,
                () -> locator.locateIndexDir("beta", "doc", "n0")
        );
        assertEquals(PathSelector.Outcome.NOT_FOUND, e.outcome());
        assertEquals("cluster", e.kind());
    }

    @Test
    void ambiguousClustersThrowsSelectionException() throws Exception {
        mkIndexTree("cluster.alpha", "n0", "doc", true);
        mkIndexTree("cluster.beta",  "n0", "doc", true);
        IndexLocator locator = new IndexLocator(tmp);

        SelectionException e = assertThrows(
                SelectionException.class,
                () -> locator.locateIndexDir(null, "doc", "n0")
        );
        assertEquals(PathSelector.Outcome.AMBIGUOUS, e.outcome());
        assertEquals("cluster", e.kind());
        assertFalse(e.options().isEmpty());
    }

    @Test
    void ambiguousNodeThrowsSelectionException() throws Exception {
        mkIndexTree("cluster.alpha", "n0", "doc", true);
        mkIndexTree("cluster.alpha", "n1", "doc", true);
        IndexLocator locator = new IndexLocator(tmp);

        SelectionException e = assertThrows(
                SelectionException.class,
                () -> locator.locateIndexDir("alpha", "doc", null)
        );
        assertEquals(PathSelector.Outcome.AMBIGUOUS, e.outcome());
        assertEquals("node-index", e.kind());
    }

    @Test
    void ambiguousSchemaThrowsSelectionException() throws Exception {
        mkIndexTree("cluster.alpha", "n0", "docA", true);
        mkIndexTree("cluster.alpha", "n0", "docB", true);
        IndexLocator locator = new IndexLocator(tmp);

        SelectionException e = assertThrows(
                SelectionException.class,
                () -> locator.locateIndexDir("alpha", null, "n0")
        );
        assertEquals(PathSelector.Outcome.AMBIGUOUS, e.outcome());
        assertEquals("schema", e.kind());
    }

    @Test
    void missingDocumentsDirThrowsNoSuchFile() throws Exception {
        // Create cluster/n0 but no "documents"
        Path base = searchRoot().resolve("cluster.alpha").resolve("n0");
        Files.createDirectories(base);
        IndexLocator locator = new IndexLocator(tmp);

        NoSuchFileException e = assertThrows(
                NoSuchFileException.class,
                () -> locator.locateIndexDir("alpha", "doc", "n0")
        );
        assertTrue(e.getMessage().contains("Documents directory"));
    }

    @Test
    void missingIndexDirThrowsNoSuchFile() throws Exception {
        // Build to 0.ready/index but omit index.flush.1
        mkIndexTree("cluster.alpha", "n0", "doc", false);
        IndexLocator locator = new IndexLocator(tmp);

        NoSuchFileException e = assertThrows(
                NoSuchFileException.class,
                () -> locator.locateIndexDir("alpha", "doc", "n0")
        );
        assertTrue(e.getMessage().contains("Index directory"));
    }

    @Test
    void missingSearchRootThrowsNoSuchFile() {
        IndexLocator locator = new IndexLocator(tmp);
        assertThrows(NoSuchFileException.class, () -> locator.locateIndexDir(null, null, null));
    }
}
