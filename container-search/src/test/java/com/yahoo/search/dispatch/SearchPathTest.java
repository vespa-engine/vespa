// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.dispatch.SearchPath.InvalidSearchPathException;
import com.yahoo.search.dispatch.searchcluster.Node;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author ollivir
 */
public class SearchPathTest {

    @Test
    public void requreThatSearchPathsAreParsedCorrectly() {
        assertEquals(SearchPath.fromString("0/0").get().toString(), "0/0");
        assertEquals(SearchPath.fromString("1/0").get().toString(), "1/0");
        assertEquals(SearchPath.fromString("0/1").get().toString(), "0/1");

        assertEquals(SearchPath.fromString("0,1/2").get().toString(), "0,1/2");
        assertEquals(SearchPath.fromString("0,1/1,2").get().toString(), "0,1/1,2");
        assertEquals(SearchPath.fromString("[0,1>/2").get().toString(), "0/2");
        assertEquals(SearchPath.fromString("[0,1>/[2,3>").get().toString(), "0/2");
        assertEquals(SearchPath.fromString("[0,2>/2").get().toString(), "[0,2>/2");
        assertEquals(SearchPath.fromString("[0,2>/[0,2>").get().toString(), "[0,2>/[0,2>");
        assertEquals(SearchPath.fromString("[0,1>,1/2").get().toString(), "0,1/2");
        assertEquals(SearchPath.fromString("[0,1>,1/[0,1>,1").get().toString(), "0,1/0,1");

        assertEquals(SearchPath.fromString("*/2").get().toString(), "/2");
        assertEquals(SearchPath.fromString("1,*/2").get().toString(), "/2");

        assertEquals(SearchPath.fromString("1").get().toString(), "1");
        assertEquals(SearchPath.fromString("1/").get().toString(), "1");
        assertEquals(SearchPath.fromString("1/*").get().toString(), "1");
    }

    @Test
    public void requreThatWildcardsAreDetected() {
        assertFalse(SearchPath.fromString("").isPresent());
        assertFalse(SearchPath.fromString("*/*").isPresent());
        assertFalse(SearchPath.fromString("/").isPresent());
        assertFalse(SearchPath.fromString("/*").isPresent());
        assertFalse(SearchPath.fromString("//").isPresent());
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void invalidRangeMustThrowException() {
        exception.expect(InvalidSearchPathException.class);
        SearchPath.fromString("[p,0>/0");
    }

    @Test
    public void invalidPartMustThrowException() {
        exception.expect(InvalidSearchPathException.class);
        SearchPath.fromString("p/0");
    }

    @Test
    public void invalidRowMustThrowException() {
        exception.expect(InvalidSearchPathException.class);
        SearchPath.fromString("1,2,3/r");
    }

    private void verifyRandomGroup(MockSearchCluster cluster, String searchPath, Set possibleSolutions) {
        for (int i=0; i < 100; i++) {
            String nodes = distKeysAsString(SearchPath.selectNodes(searchPath, cluster));
            assertTrue(possibleSolutions.contains(nodes));
        }
    }

    @Test
    public void searchPathMustFilterNodesBasedOnDefinition() {
        MockSearchCluster cluster = new MockSearchCluster("a",3, 3);

        assertEquals(distKeysAsString(SearchPath.selectNodes("1/1", cluster)), "4");
        assertEquals(distKeysAsString(SearchPath.selectNodes("/1", cluster)), "3,4,5");
        assertEquals(distKeysAsString(SearchPath.selectNodes("0,1/2", cluster)), "6,7");
        assertEquals(distKeysAsString(SearchPath.selectNodes("[1,3>/1", cluster)), "4,5");
        assertEquals(distKeysAsString(SearchPath.selectNodes("[1,88>/1", cluster)), "4,5");

        verifyRandomGroup(cluster, "[1,88>/", Set.of("1,2", "4,5", "7,8"));
        verifyRandomGroup(cluster, "[1,88>/0", Set.of("1,2"));
        verifyRandomGroup(cluster, "[1,88>/2", Set.of("7,8"));
        verifyRandomGroup(cluster, "[1,88>/0,2", Set.of("1,2", "7,8"));
    }

    private static String distKeysAsString(Collection<Node> nodes) {
        return nodes.stream().map(Node::key).map(Object::toString).collect(Collectors.joining(","));
    }
}
