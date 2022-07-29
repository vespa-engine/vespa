// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class SearchClusterCoverageTest {

    @Test
    void two_groups_equal_docs() {
        var tester =  new SearchClusterTester(2, 3);

        tester.setDocsPerNode(100, 0);
        tester.setDocsPerNode(100, 1);
        tester.pingIterationCompleted();
        assertTrue(tester.group(0).hasSufficientCoverage());
        assertTrue(tester.group(1).hasSufficientCoverage());
    }

    @Test
    void two_groups_one_missing_docs() {
        var tester =  new SearchClusterTester(2, 3);

        tester.setDocsPerNode(100, 0);
        tester.setDocsPerNode(70, 1);
        tester.pingIterationCompleted();
        assertTrue(tester.group(0).hasSufficientCoverage());
        assertFalse(tester.group(1).hasSufficientCoverage());
    }

    @Test
    void three_groups_one_missing_docs() {
        var tester =  new SearchClusterTester(3, 3);

        tester.setDocsPerNode(100, 0);
        tester.setDocsPerNode(87, 1);  // min is set to 88 in MockSearchCluster
        tester.setDocsPerNode(100, 2);
        tester.pingIterationCompleted();
        assertTrue(tester.group(0).hasSufficientCoverage());
        assertFalse(tester.group(1).hasSufficientCoverage());
        assertTrue(tester.group(2).hasSufficientCoverage());
    }

    @Test
    void three_groups_one_missing_docs_but_too_few() {
        var tester =  new SearchClusterTester(3, 3);

        tester.setDocsPerNode(100, 0);
        tester.setDocsPerNode(89, 1);  // min is set to 88 in MockSearchCluster
        tester.setDocsPerNode(100, 2);
        tester.pingIterationCompleted();
        assertTrue(tester.group(0).hasSufficientCoverage());
        assertTrue(tester.group(1).hasSufficientCoverage());
        assertTrue(tester.group(2).hasSufficientCoverage());
    }

    @Test
    void three_groups_one_has_too_many_docs() {
        var tester =  new SearchClusterTester(3, 3);

        tester.setDocsPerNode(100, 0);
        tester.setDocsPerNode(150, 1);
        tester.setDocsPerNode(100, 2);
        tester.pingIterationCompleted();
        assertTrue(tester.group(0).hasSufficientCoverage());
        assertTrue(tester.group(1).hasSufficientCoverage());
        assertTrue(tester.group(2).hasSufficientCoverage());
    }

    @Test
    void three_groups_one_has_a_node_down() {
        var tester = new SearchClusterTester(3, 3);

        tester.setDocsPerNode(100, 0);
        tester.setDocsPerNode(100, 1);
        tester.setDocsPerNode(100, 2);
        tester.setWorking(1, 1, false);
        tester.pingIterationCompleted();
        assertTrue(tester.group(0).hasSufficientCoverage());
        assertFalse(tester.group(1).hasSufficientCoverage());
        assertTrue(tester.group(2).hasSufficientCoverage());
    }

    @Test
    void three_groups_one_has_a_node_down_but_remaining_has_enough_docs() {
        var tester = new SearchClusterTester(3, 3);

        tester.setDocsPerNode(100, 0);
        tester.setDocsPerNode(150, 1);
        tester.setDocsPerNode(100, 2);
        tester.setWorking(1, 1, false);
        tester.pingIterationCompleted();
        assertTrue(tester.group(0).hasSufficientCoverage());
        assertTrue(tester.group(1).hasSufficientCoverage(), "Sufficient documents on remaining two nodes");
        assertTrue(tester.group(2).hasSufficientCoverage());
    }

    @Test
    void one_group_few_docs_unbalanced() {
        var tester = new SearchClusterTester(1, 2);

        Node node0 = tester.group(0).nodes().get(0);
        Node node1 = tester.group(0).nodes().get(1);

        // 1 document
        node0.setWorking(true);
        node1.setWorking(true);

        node0.setActiveDocuments(1);
        node1.setActiveDocuments(0);

        tester.pingIterationCompleted();
        assertFalse(tester.group(0).isBalanced());
        assertTrue(tester.group(0).isSparse());
    }

    @Test
    void one_group_many_docs_unbalanced() {
        var tester = new SearchClusterTester(1, 2);

        Node node0 = tester.group(0).nodes().get(0);
        Node node1 = tester.group(0).nodes().get(1);

        // 1 document
        node0.setWorking(true);
        node1.setWorking(true);

        node0.setActiveDocuments(1000000);
        node1.setActiveDocuments(100000);

        tester.pingIterationCompleted();
        assertFalse(tester.group(0).isBalanced());
        assertFalse(tester.group(0).isSparse());
    }

}
