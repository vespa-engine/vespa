// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.vespasignificance.export;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests outcome of {@link PathSelector}.
 *
 * @author johsol
 */
public class PathSelectorTest {
    private static final String KIND = "cluster";
    private static final Path CONTAINER = Path.of("/vespa/var/db/vespa/search");

    private static String name(Path p) {
        return p.getFileName().toString();
    }

    private static String path(Path p) {
        return p.toString();
    }

    @Test
    void emptyItemsReturnsNotFound() {
        var res = PathSelector.selectOne(
                List.<Path>of(),
                null,
                KIND,
                CONTAINER,
                PathSelectorTest::name,
                PathSelectorTest::path
        );

        assertEquals(PathSelector.Outcome.NOT_FOUND, res.outcome());
        assertNull(res.value());
        assertTrue(res.options().isEmpty());
    }

    @Test
    void singleItemAndNoPreferenceReturnsChosen() {
        var only = Path.of("/root/cluster.alpha");
        var res = PathSelector.selectOne(
                List.of(only),
                null,
                KIND,
                CONTAINER,
                PathSelectorTest::name,
                PathSelectorTest::path
        );

        assertEquals(PathSelector.Outcome.CHOSEN, res.outcome());
        assertEquals(only, res.value());
        assertEquals("", res.message());
        assertTrue(res.options().isEmpty());
    }

    @Test
    void multipleItemsNoPreferenceReturnsAmbiguous() {
        var a = Path.of("/root/cluster.beta");
        var b = Path.of("/root/cluster.Alpha");
        var c = Path.of("/root/cluster.gamma");

        var res = PathSelector.selectOne(
                List.of(a, b, c),
                null,
                KIND,
                CONTAINER,
                PathSelectorTest::name,
                PathSelectorTest::path
        );

        assertEquals(PathSelector.Outcome.AMBIGUOUS, res.outcome());
        assertNull(res.value());
        assertEquals(3, res.options().size());
    }

    @Test
    void multipleItemsWithExactPreferenceMatchesChosen() {
        var a = Path.of("/root/cluster.alpha");
        var b = Path.of("/root/cluster.beta");
        var res = PathSelector.selectOne(
                List.of(a, b),
                "cluster.beta",
                KIND,
                CONTAINER,
                PathSelectorTest::name,
                PathSelectorTest::path
        );

        assertEquals(PathSelector.Outcome.CHOSEN, res.outcome());
        assertEquals(b, res.value());
    }

    @Test
    void multipleItemsPreferenceNotFound() {
        var a = Path.of("/root/cluster.alpha");
        var b = Path.of("/root/cluster.beta");

        var res = PathSelector.selectOne(
                List.of(a, b),
                "cluster.gamma",
                KIND,
                CONTAINER,
                PathSelectorTest::name,
                PathSelectorTest::path
        );

        assertEquals(PathSelector.Outcome.NOT_FOUND, res.outcome());
        assertNull(res.value());
        assertEquals(2, res.options().size());
    }
}
