// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.distribution;

import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.document.BucketId;
import com.yahoo.vdslib.state.NodeType;
import org.junit.After;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Stack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DistributionTestCase {

    private static final int minUsedBits = 16;

    private DistributionTestFactory test;
    /** Build a set of buckets to test that should represent the entire bucket space well. */
    private static List<BucketId> getTestBuckets() {
        List<BucketId> buckets = new ArrayList<>();
        // Get a set of buckets from the same split level
        for (int i = 16; i <= 18; ++ i) {
            for (int j = 0; j < 20; ++ j) {
                buckets.add(new BucketId(i, j));
            }
        }
        // Get a few random buckets at every split level.
        Random randomized = new Random(413);
        long randValue = randomized.nextLong();
        for (int i = minUsedBits; i < 58; ++ i) {
            buckets.add(new BucketId(i, randValue));
        }
        randValue = randomized.nextLong();
        for (int i = minUsedBits; i < 58; ++ i) {
            buckets.add(new BucketId(i, randValue));
        }
        return Collections.unmodifiableList(buckets);
    }

    @After
    public void tearDown() throws Exception {
        if (test != null) {
            System.err.println("Verified " + test.getVerifiedTests() + " test results for test " + test.getName());
            test.recordTestResults();
        }
    }

    @Test
    public void testSimple() {
        test = new DistributionTestFactory("simple");
        List<BucketId> buckets = getTestBuckets();
        Integer [] nodes = { 6, 3, 4, 8, 8, 8, 8, 8, 8, 3 };
        for (int i=0; i<buckets.size(); ++i) {
            BucketId bucket = buckets.get(i);
            DistributionTestFactory.Test t = test.recordResult(bucket).assertNodeCount(1);
            if (i < nodes.length) {
                t.assertNodeUsed(nodes[i]);
            }
        }
    }

    @Test
    public void testDown() throws Exception {
        test = new DistributionTestFactory("down")
                .setUpStates("u")
                .setClusterState(new ClusterState(
                        "distributor:10 .4.s:m .5.s:m .6.s:d .7.s:d .8.s:r .9.s:r"));
        for (BucketId bucket : getTestBuckets()) {
            assertTrue(test.recordResult(bucket).assertNodeCount(1).getNodes().get(0) < 4);
        }
    }

    /**
     * The java side runs unit tests first. Thus java side will generate the distribution tests that the C++ side will verify equality with.
     * The tests serialized by the java side will be checked into version control, such that C++ side can test without java side. When one of the sides
     * change, the failing side can be identified by checking if the serialized files are modified from what is checked into version control.
     */
    private void writeDistributionTest(String name, String clusterState, String distributionConfig) throws IOException, ParseException, Distribution.TooFewBucketBitsInUseException, Distribution.NoDistributorsAvailableException {
        writeFileAtomically("src/tests/distribution/testdata/java_" + name + ".cfg", distributionConfig);
        writeFileAtomically("src/tests/distribution/testdata/java_" + name + ".state", clusterState);
        StringWriter sw = new StringWriter();
        Distribution distribution = new Distribution("raw:" + distributionConfig);
        ClusterState state = new ClusterState(clusterState);
        long maxBucket = 1;
        for (int distributionBits = 0; distributionBits <= 32; ++distributionBits) {
            state.setDistributionBits(distributionBits);
            RandomGen randomizer = new RandomGen(distributionBits);
            for (int bucketIndex = 0; bucketIndex < 64; ++bucketIndex) {
                if (bucketIndex >= maxBucket) {
                    break;
                }
                long bucketId = bucketIndex;
                    // Use random bucket if we dont test all
                if (maxBucket > 64) {
                    bucketId = randomizer.nextLong();
                }
                BucketId bucket = new BucketId(distributionBits, bucketId);
                for (int redundancy = 1; redundancy <= distribution.getRedundancy(); ++redundancy) {
                    int distributorIndex = distribution.getIdealDistributorNode(state, bucket, "uim");
                    sw.write(distributionBits + " " + bucket.withoutCountBits() + " " + redundancy + " " + distributorIndex + "\n");
                }
            }
            maxBucket = maxBucket << 1;
        }

        writeFileAtomically("src/tests/distribution/testdata/java_" + name + ".distribution", sw.toString());
    }

    private void writeFileAtomically(String filename, String data) throws IOException {
        FileSystem fs = FileSystems.getDefault();
        Path filePath = fs.getPath(filename);
        Path tempFilePath = fs.getPath(filename + ".tmp");

        try (BufferedWriter bw = Files.newBufferedWriter(tempFilePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            bw.write(data);
        }

        // Try to atomically move temporary file onto file. This is guaranteed to be atomic due to the files existing in the same file system
        Files.move(tempFilePath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Test
    public void testWriteDistribution() throws IOException, ParseException, Distribution.TooFewBucketBitsInUseException, Distribution.NoDistributorsAvailableException {
        String clusterState = "distributor:9";
        String distributionConfig =
                """
                        redundancy 3
                        group[4]
                        group[0].index "invalid"
                        group[0].name "invalid"
                        group[0].partitions 1|2|*
                        group[0].nodes[0]
                        group[1].index 1
                        group[1].capacity 2.0
                        group[1].name group1
                        group[1].partitions *
                        group[1].nodes[3]
                        group[1].nodes[0].index 0
                        group[1].nodes[1].index 1
                        group[1].nodes[2].index 2
                        group[2].index 2
                        group[2].capacity 3.0
                        group[2].name group2
                        group[2].partitions *
                        group[2].nodes[3]
                        group[2].nodes[0].index 3
                        group[2].nodes[1].index 4
                        group[2].nodes[2].index 5
                        group[3].index 3
                        group[3].capacity 5.0
                        group[3].name group3
                        group[3].partitions *
                        group[3].nodes[3]
                        group[3].nodes[0].index 6
                        group[3].nodes[1].index 7
                        group[3].nodes[2].index 8
                        """;
        writeDistributionTest("depth2", clusterState, distributionConfig);

        clusterState = "distributor:20 storage:20";
        String complexDistributionConfig =
                """
                        redundancy 5
                        group[7]
                        group[0].partitions "*|*"
                        group[0].index "invalid"
                        group[0].name "invalid"
                        group[0].nodes[0]
                        group[1].partitions "1|*"
                        group[1].index "0"
                        group[1].name "switch0"
                        group[1].nodes[0]
                        group[2].partitions ""
                        group[2].index "0.0"
                        group[2].name "rack0"
                        group[2].nodes[4]
                        group[2].nodes[0].index 0
                        group[2].nodes[1].index 1
                        group[2].nodes[2].index 2
                        group[2].nodes[3].index 3
                        group[3].partitions ""
                        group[3].index "0.1"
                        group[3].name "rack1"
                        group[3].nodes[4]
                        group[3].nodes[0].index 8
                        group[3].nodes[1].index 9
                        group[3].nodes[2].index 14
                        group[3].nodes[3].index 15
                        group[4].partitions "*"
                        group[4].index "1"
                        group[4].name "switch1"
                        group[4].nodes[0]
                        group[5].partitions ""
                        group[5].index "1.0"
                        group[5].name "rack0"
                        group[5].nodes[4]
                        group[5].nodes[0].index 4
                        group[5].nodes[1].index 5
                        group[5].nodes[2].index 6
                        group[5].nodes[3].index 17
                        group[6].partitions ""
                        group[6].index "1.1"
                        group[6].name "rack1"
                        group[6].nodes[4]
                        group[6].nodes[0].index 10
                        group[6].nodes[1].index 12
                        group[6].nodes[2].index 13
                        group[6].nodes[3].index 7""";
        writeDistributionTest("depth3", clusterState, complexDistributionConfig);

        clusterState = "distributor:20 storage:20 .3.c:3 .7.c:2.5 .12.c:1.5";
        writeDistributionTest("capacity", clusterState, complexDistributionConfig);

        clusterState = "distributor:20 storage:20 .3.r:2 .7.r:3 .12.r:5";
        writeDistributionTest("retired", clusterState, complexDistributionConfig);
    }

    @Test
    public void testSplitBeyondSplitBitDoesntAffectDistribution() {
        Random randomized = new Random(7123161);
        long val = randomized.nextLong();
        test = new DistributionTestFactory("abovesplitbit");
        for (int i=16; i<=58; ++i) {
            test.recordResult(new BucketId(i, val)).assertNodeUsed(2);
        }
    }

    @Test
    public void testMinimalMovement() throws Exception {
        test = new DistributionTestFactory("minimal-movement")
                .setClusterState(new ClusterState("distributor:4 .2.s:d"));
        DistributionTestFactory control = new DistributionTestFactory("minimal-movement-reference")
                .setClusterState(new ClusterState("distributor:4"));
        int moved = 0;
        int staying = 0;
        for (BucketId bucket : getTestBuckets()) {
            DistributionTestFactory.Test org = control.recordResult(bucket).assertNodeCount(1);
            DistributionTestFactory.Test res = test.recordResult(bucket).assertNodeCount(1);
            if (org.getNodes().get(0) == 2) {
                assertTrue(res.getNodes().get(0) != 2);
                ++moved;
            } else {
                assertEquals(org, res);
                ++staying;
            }
        }
        assertEquals(63, moved);
        assertEquals(81, staying);
    }

    @Test
    public void testAllDistributionBits() throws Exception {
        for (int distbits=0; distbits<=32; ++distbits) {
            test = new DistributionTestFactory("distbit" + distbits)
                    .setClusterState(new ClusterState("bits:" + distbits + " distributor:10"));
            List<BucketId> buckets = new ArrayList<>();
            for (int i=0; i<100; ++i) {
                buckets.add(new BucketId(distbits, i));
            }
            for (BucketId bucket : buckets) {
                test.recordResult(bucket).assertNodeCount(1);
            }
            test.recordTestResults();
            test = null;
        }
    }

    private int getNodeCount(int depth, int branchCount, int nodesPerLeaf) {
        if (depth <= 1) {
            return branchCount * nodesPerLeaf;
        }
        int count = 0;
        for (int i=0; i<branchCount; ++i) {
            count += getNodeCount(depth - 1, branchCount, nodesPerLeaf);
        }
        return count;
    }
    private StorDistributionConfig.Builder buildHierarchicalConfig(
            int redundancy, int branchCount, int depth, String partitions, int nodesPerLeaf)
    {
        StorDistributionConfig.Builder builder = new StorDistributionConfig.Builder()
                .redundancy(redundancy);
        builder.group(new StorDistributionConfig.Group.Builder()
                .name("invalid").index("invalid").partitions(partitions));
        Stack<Integer> nodeIndexes = new Stack<>();
        for (int i=0, n=getNodeCount(depth, branchCount, nodesPerLeaf); i<n; ++i) {
            nodeIndexes.push(i);
        }
        Collections.shuffle(nodeIndexes, new Random(123));
        addLevel(builder, "top", "", branchCount, depth, partitions, nodesPerLeaf, nodeIndexes);
        return builder;
    }
    private void addLevel(StorDistributionConfig.Builder builder, String namePrefix, String indexPrefix,
                          int branchCount, int depth, String partitions, int nodesPerLeaf,
                          Stack<Integer> nodes)
    {
        for (int i=0; i<branchCount; ++i) {
            StorDistributionConfig.Group.Builder group
                    = new StorDistributionConfig.Group.Builder();
            String gname = namePrefix + "." + i;
            String index = (indexPrefix.isEmpty() ? "" + i : indexPrefix + "." + i);
            group.name(gname).index(index);
            if (depth <= 1) {
                for (int j=0; j<nodesPerLeaf; ++j) {
                    group.nodes(new StorDistributionConfig.Group.Nodes.Builder().index(nodes.pop()));
                }
            } else {
                group.partitions(partitions);
            }
            builder.group(group);
            if (depth > 1) {
                addLevel(builder, gname, index, branchCount, depth - 1, partitions, nodesPerLeaf, nodes);
            }
        }
    }

    @Test
    public void testHierarchicalDistribution() {
        test = new DistributionTestFactory("hierarchical-grouping")
                .setDistribution(buildHierarchicalConfig(6, 3, 1, "1|2|*", 3));
        for (BucketId bucket : getTestBuckets()) {
            test.recordResult(bucket).assertNodeCount(1);
        }
    }

    @Test
    public void testDistributorGroupTakeover() throws Exception {
        test = new DistributionTestFactory("hierarchical-grouping-distributor-takeover")
                .setDistribution(buildHierarchicalConfig(6, 3, 1, "1|2|*", 3))
                .setNodeType(NodeType.DISTRIBUTOR)
                .setClusterState(new ClusterState("distributor:2 storage:9"));
        for (BucketId bucket : getTestBuckets()) {
            test.recordResult(bucket).assertNodeCount(1);
        }
    }

    @Test
    public void testHierarchicalDistributionDeep() throws Exception {
        test = new DistributionTestFactory("hierarchical-grouping-deep")
                .setNodeCount(500)
                .setDistribution(buildHierarchicalConfig(8, 5, 3, "*|*", 3));
        for (BucketId bucket : getTestBuckets()) {
            test.recordResult(bucket).assertNodeCount(1);
        }
        Set<ConfiguredNode> nodes = test.getDistribution().getNodes();
        // Despite setNodeCount(500) above, the actual distribution config
        // itself only has 375 actual leaf nodes.
        assertEquals(375, nodes.size());
    }

    @Test
    public void testHierarchicalDistributionCapacity() throws Exception {
        StorDistributionConfig.Builder config = buildHierarchicalConfig(6, 3, 1, "1|*", 3);
        config.group.get(1).capacity(3);
        test = new DistributionTestFactory("group-capacity")
                .setNodeCount(getNodeCount(1, 3, 3)).setDistribution(config);

        int [] counts = new int[9];
        for (int i=0; i<900; ++i) {
            BucketId bucket = new BucketId(16, i);
            ++counts[ test.recordResult(bucket).assertNodeCount(1).getNodes().get(0) ];
        }
        int groupCount = 0;
        for (StorDistributionConfig.Group.Nodes.Builder n : config.group.get(1).nodes) {
            StorDistributionConfig.Group.Nodes node = new StorDistributionConfig.Group.Nodes(n);
            groupCount += counts[ node.index() ];
        }
        int avg3 = groupCount / 3;
        int avg1 = (900 - groupCount) / 6;
        double diff = 1.0 * avg3 / avg1;
        assertTrue(Arrays.toString(counts) + ": Too large diff" + diff, diff < 3.1);
        assertTrue(Arrays.toString(counts) + ": Too small diff" + diff, diff > 2.9);
    }

    @Test(expected = Distribution.NoDistributorsAvailableException.class)
    public void clusterDownInHierarchicSetupThrowsNoDistributorsAvailableException() throws Exception {
        ClusterState clusterState = new ClusterState("cluster:d");

        StorDistributionConfig.Builder config = buildHierarchicalConfig(4, 4, 1, "1|1|1|*", 1);
        Distribution distr = new Distribution(new StorDistributionConfig(config));
        distr.getIdealDistributorNode(clusterState, new BucketId(16, 0), "uim");
    }

    @Test
    public void relative_node_order_scoring_is_distribution_key_invariant() throws Exception {
        // 3 groups of 3 nodes, redundancy 6 globally, i.e. 2 within each group
        StorDistributionConfig.Builder config = buildHierarchicalConfig(6, 3, 1, "*|*|*", 3);
        config.relative_node_order_scoring(true);
        var distr = new Distribution(new StorDistributionConfig(config));
        var allUpState = new ClusterState("version:1 storage:9 distributor:9");
        // Subtle difference between C++ and Java test setup; in C++ the group node distribution
        // keys are [0, 1, 2], [3, 4, 5], [6, 7, 8], whereas in Java they are [8, 1, 6], [5, 0, 2], [4, 3, 7].
        //                                     0  1  2  3  4  5  6  7  8
        int[] keyToRelativeIndex = new int[] { 1, 1, 2, 1, 0, 0, 2, 2, 0 };
        int nBuckets = 1000;
        for (int i = 0; i < nBuckets; ++i) {
            var bucketId = new BucketId(16, i);
            var nodes = distr.getIdealStorageNodes(allUpState, bucketId, "ui");
            assertEquals(6, nodes.size());
            // All replicas should be placed on nodes that have the same _relative_ configured
            // intra-group ordering across all groups.
            assertEquals(bucketId.toString(), keyToRelativeIndex[nodes.get(0)], keyToRelativeIndex[nodes.get(2)]);
            assertEquals(bucketId.toString(), keyToRelativeIndex[nodes.get(0)], keyToRelativeIndex[nodes.get(4)]);

            assertEquals(bucketId.toString(), keyToRelativeIndex[nodes.get(1)], keyToRelativeIndex[nodes.get(3)]);
            assertEquals(bucketId.toString(), keyToRelativeIndex[nodes.get(1)], keyToRelativeIndex[nodes.get(5)]);

            int distributorNode = distr.getIdealDistributorNode(allUpState, bucketId, "u");
            assertEquals((int)nodes.get(0), distributorNode);
        }
    }

    private StorDistributionConfig.Builder buildFlatConfigWithRelativeScoring(List<Integer> nodeKeys) {
        StorDistributionConfig.Builder builder = new StorDistributionConfig.Builder()
                .redundancy(1)
                .relative_node_order_scoring(true);
        var group = new StorDistributionConfig.Group.Builder()
                .name("invalid")
                .index("invalid")
                .partitions("*");
        nodeKeys.forEach(n -> group.nodes(new StorDistributionConfig.Group.Nodes.Builder().index(n)));
        builder.group(group);
        return builder;
    }

    @Test
    public void relative_order_scoring_allows_for_verbatim_node_replacement_via_retiring() throws Exception {
        var cfg8Nodes = buildFlatConfigWithRelativeScoring(List.of(0, 1, 2, 3, 4, 5, 6, 7));
        // Note that node 8 is configured _right after_ 5.
        // With relative index scoring, config order matters!
        var cfg9Nodes = buildFlatConfigWithRelativeScoring(List.of(0, 1, 2, 3, 4, 5, 8, 6, 7));

        var distr8 = new Distribution(new StorDistributionConfig(cfg8Nodes));
        var distr9 = new Distribution(new StorDistributionConfig(cfg9Nodes));
        var allUpState = new ClusterState("version:1 storage:8 distributor:8");
        // In this state, node 5 is retired with node 8 logically taking up its "slot"
        // in the relative index order of the ideal state computation. This means that
        // all its buckets will intrinsically belong to node 8, and no other buckets
        // in the system will move. This hinges on group node order _not_ being implicitly
        // normalized to be in distribution key order as part of configuration.
        var oneRetiredState = new ClusterState("version:1 storage:9 .5.s:r distributor:9");

        for (int i = 0; i < 10_000; ++i) {
            var bucketId = new BucketId(16, i);
            var origNodes = distr8.getIdealStorageNodes(allUpState, bucketId, "ui");
            assertEquals(1, origNodes.size());

            var newNodes = distr9.getIdealStorageNodes(oneRetiredState, bucketId, "ui");
            assertEquals(1, newNodes.size());
            if (origNodes.get(0) == 5) {
                // Should be taken over by node 8
                assertEquals(bucketId.toString(), 8, (long)newNodes.get(0));
            } else {
                // Node should be unchanged
                assertEquals(bucketId.toString(), origNodes.get(0), newNodes.get(0));
            }
        }
    }

    private DistributionTestFactory makeRelativeScoring3x4TestFactory(String testName, NodeType nodeType, String clusterStateString) throws Exception {
        // groups: [2, 9, 6, 8], [11, 4, 0, 7], [3, 1, 10, 5]
        // 2 replicas within each group.
        StorDistributionConfig.Builder config = buildHierarchicalConfig(6, 3, 1, "*|*|*", 4);
        config.relative_node_order_scoring(true);
        return new DistributionTestFactory(testName)
                .setNodeCount(12)
                .setRedundancy(6)
                .setNodeType(nodeType)
                .setClusterState(new ClusterState(clusterStateString))
                .setDistribution(config);
    }

    @Test
    public void relative_distributor_scoring_matches_cross_language_all_nodes_up() throws Exception {
        test = makeRelativeScoring3x4TestFactory("relative-scoring-all-distributors-up",
                NodeType.DISTRIBUTOR, "version:1 storage:12 distributor:12");
        for (BucketId bucket : getTestBuckets()) {
            test.recordResult(bucket).assertNodeCount(1);
        }
    }

    @Test
    public void relative_distributor_scoring_matches_cross_language_some_nodes_down() throws Exception {
        test = makeRelativeScoring3x4TestFactory("relative-scoring-some-distributors-down",
                NodeType.DISTRIBUTOR, "version:1 storage:12 distributor:12 .1.s:d .2.s:d .5.s:d .7.s:d");
        for (BucketId bucket : getTestBuckets()) {
            test.recordResult(bucket).assertNodeCount(1).assertNodesNotUsed(1, 2, 5, 7);
        }
    }

    @Test
    public void relative_distributor_scoring_matches_cross_language_group_failover() throws Exception {
        test = makeRelativeScoring3x4TestFactory("relative-scoring-distributor-group-failover",
                NodeType.DISTRIBUTOR, "version:1 storage:12 distributor:12 .1.s:d .3.s:d .5.s:d .10.s:d");
        for (BucketId bucket : getTestBuckets()) {
            test.recordResult(bucket).assertNodeCount(1).assertNodesNotUsed(3, 1, 10, 5);
        }
    }

    @Test
    public void relative_distributor_scoring_matches_cross_language_retired_storage_node() throws Exception {
        test = makeRelativeScoring3x4TestFactory("relative-scoring-distributor-with-storagenode-retired",
                NodeType.DISTRIBUTOR, "version:1 storage:12 .4.s:r distributor:12");
        for (BucketId bucket : getTestBuckets()) {
            // Distributors should not be affected by storage nodes in retired mode
            test.recordResult(bucket).assertNodeCount(1);
        }
    }

    @Test
    public void relative_storage_node_scoring_matches_cross_language_all_nodes_up() throws Exception {
        test = makeRelativeScoring3x4TestFactory("relative-scoring-all-storagenodes-up",
                NodeType.STORAGE, "version:1 storage:12 distributor:12");
        for (BucketId bucket : getTestBuckets()) {
            test.recordResult(bucket).assertNodeCount(6);
        }
    }

    @Test
    public void relative_storage_node_scoring_matches_cross_language_some_nodes_down() throws Exception {
        test = makeRelativeScoring3x4TestFactory("relative-node-index-some-storagenodes-down",
                NodeType.STORAGE, "version:1 storage:12 .1.s:d .2.s:d .5.s:d .7.s:d distributor:12");
        for (BucketId bucket : getTestBuckets()) {
            test.recordResult(bucket).assertNodeCount(6).assertNodesNotUsed(1, 2, 5, 7);
        }
    }

    @Test
    public void relative_storage_node_scoring_matches_cross_language_whole_group_down() throws Exception {
        test = makeRelativeScoring3x4TestFactory("relative-scoring-storagenode-group-down",
                NodeType.STORAGE, "version:1 storage:12 .1.s:d .3.s:d .5.s:d .10.s:d distributor:12");
        for (BucketId bucket : getTestBuckets()) {
            // only 2 groups can 2-way replicate data each
            test.recordResult(bucket).assertNodeCount(4).assertNodesNotUsed(3, 1, 10, 5);
        }
    }

    @Test
    public void relative_storage_node_scoring_matches_cross_language_retired_node() throws Exception {
        test = makeRelativeScoring3x4TestFactory("relative-scoring-storagenode-retired",
                NodeType.STORAGE, "version:1 storage:12 .4.s:r distributor:12");
        for (BucketId bucket : getTestBuckets()) {
            test.recordResult(bucket).assertNodeCount(6).assertNodesNotUsed(4);
        }
    }

    @Test
    public void relative_storage_node_scoring_matches_cross_language_maintenance_nodes() throws Exception {
        test = makeRelativeScoring3x4TestFactory("relative-scoring-storagenode-maintenance",
                NodeType.STORAGE, "version:1 storage:12 .5.s:m .9.s:m distributor:12");
        test.setUpStates("ui"); // Fixture uses "uim" by default, which includes Maintenance nodes
        for (BucketId bucket : getTestBuckets()) {
            test.recordResult(bucket).assertNodeCount(6).assertNodesNotUsed(5, 9);
        }
    }

}
