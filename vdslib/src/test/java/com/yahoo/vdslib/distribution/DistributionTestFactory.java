// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.distribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.document.BucketId;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.config.content.StorDistributionConfig;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


// TODO: Use config builder instead of ConfigGetter to create test config.
public class DistributionTestFactory extends CrossPlatformTestFactory {

    ObjectMapper mapper = new ObjectMapper();

    private static final String testDirectory = "src/tests/distribution/testdata";
    private int redundancy;
    private int nodeCount;
    private ClusterState state;
    private StorDistributionConfig.Builder distributionConfig;
    private NodeType nodeType;
    private String upStates;

    private int testsRecorded = 0;
    private List<Test> results = new ArrayList<>();
    private int testsVerified = 0;

    enum Failure { NONE, TOO_FEW_BITS, NO_DISTRIBUTORS_AVAILABLE };

    static public class Test {
        private BucketId bucket;
        private List<Integer> nodes;
        private Failure failure;

        public Test(BucketId bucket) {
            this.bucket = bucket;
            nodes = new ArrayList<>();
            failure = Failure.NONE;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Test)) return false;
            Test t = (Test) other;
            return (bucket.equals(t.bucket)
                    && nodes.equals(t.nodes)
                    && failure.equals(t.failure));
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(bucket, nodes);
        }

        public String toString() {
            StringBuilder sb = new StringBuilder().append(bucket.toString());
            if (failure == Failure.NONE) {
                sb.append(" [");
                for (int i=0; i<nodes.size(); ++i) {
                    if (i != 0) sb.append(" ");
                    sb.append(nodes.get(i));
                }
                sb.append("]");
            } else {
                sb.append(' ').append(failure);
            }
            return sb.toString();
        }

        public List<Integer> getNodes() {
            return nodes;
        }

        public Test assertFailure(Failure f) {
            assertEquals(f, failure);
            return this;
        }
        public Test assertNodeCount(int count) {
            if (count > 0) assertEquals(toString(), Failure.NONE, failure);
            assertEquals(toString(), count, nodes.size());
            return this;
        }
        public Test assertNodeUsed(int node) {
            assertEquals(toString(), Failure.NONE, failure);
            assertTrue(toString(), nodes.contains(node));
            return this;
        }
    }

    public DistributionTestFactory(String name) {
        super(testDirectory, name);
        try{
            redundancy = 3;
            nodeCount = 10;
            state = new ClusterState("distributor:" + nodeCount);
            distributionConfig = deserializeConfig(Distribution.getDefaultDistributionConfig(redundancy, nodeCount));
            nodeType = NodeType.DISTRIBUTOR;
            upStates = "uim";
            loadTestResults();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("deprecation")
    private static StorDistributionConfig.Builder deserializeConfig(String s) {
        return new StorDistributionConfig.Builder(
                new ConfigGetter<>(StorDistributionConfig.class).getConfig("raw:" + s));
    }

    public DistributionTestFactory setNodeCount(int count) throws Exception {
        nodeCount = count;
        distributionConfig = deserializeConfig(Distribution.getDefaultDistributionConfig(redundancy, nodeCount));
        state = new ClusterState("distributor:" + nodeCount);
        return this;
    }

    public DistributionTestFactory setClusterState(ClusterState state) {
        this.state = state;
        return this;
    }

    public DistributionTestFactory setDistribution(StorDistributionConfig.Builder d) {
        this.distributionConfig = d;
        return this;
    }

    public Distribution getDistribution() {
        return new Distribution(new StorDistributionConfig(distributionConfig));
    }

    public DistributionTestFactory setNodeType(NodeType n) {
        this.nodeType = n;
        return this;
    }

    public DistributionTestFactory setUpStates(String up) {
        this.upStates = up;
        return this;
    }

    public int getVerifiedTests() {
        return testsVerified;
    }

    void verifySame(Test javaTest, Test other) {
        assertEquals("Reference test " + testsRecorded + " differ.", other, javaTest);
        ++testsVerified;
    }

    Test recordResult(BucketId bucket) {
        Test t = new Test(bucket);
        Distribution d = new Distribution(new StorDistributionConfig(distributionConfig));
        try{
            if (nodeType.equals(NodeType.DISTRIBUTOR)) {
                int node = d.getIdealDistributorNode(state, bucket, upStates);
                t.nodes.add(node);
            } else {
                for (int i : d.getIdealStorageNodes(state, bucket, upStates)) {
                    t.nodes.add(i);
                }
            }
        } catch (Distribution.TooFewBucketBitsInUseException e) {
            t.failure = Failure.TOO_FEW_BITS;
        } catch (Distribution.NoDistributorsAvailableException e) {
            t.failure = Failure.NO_DISTRIBUTORS_AVAILABLE;
        }
        if (results.size() > testsRecorded) {
            verifySame(t, results.get(testsRecorded));
        } else {
            results.add(t);
        }
        ++testsRecorded;
        return t;
    }

    public String serialize() throws Exception {
        ObjectNode test = new ObjectNode(mapper.getNodeFactory())
                .put("cluster-state", state.toString())
                .put("distribution", new StorDistributionConfig(distributionConfig).toString())
                .put("node-type", nodeType.toString())
                .put("redundancy", redundancy)
                .put("node-count", nodeCount)
                .put("up-states", upStates);
        ArrayNode results = test.putArray("result");
        for (Test t : this.results) {
             results.addObject()
                    .putPOJO("nodes", t.nodes)
                    .put("bucket", Long.toHexString(t.bucket.getId()))
                    .put("failure", t.failure.toString());
        }
        return test.toPrettyString();
    }

    public void parse(String serialized) throws Exception {
        JsonNode json = mapper.readTree(serialized);
        upStates = json.get("up-states").textValue();
        nodeCount = json.get("redundancy").intValue();
        redundancy = json.get("redundancy").intValue();
        state = new ClusterState(json.get("cluster-state").textValue());
        distributionConfig = deserializeConfig(json.get("distribution").textValue());
        nodeType = NodeType.get(json.get("node-type").textValue());
        for (JsonNode result : json.get("result")) {
            Test t = new Test(new BucketId(Long.parseLong(result.get("bucket").textValue(), 16)));
            for (JsonNode node : result.get("nodes")) t.nodes.add(node.intValue());
            t.failure = Failure.valueOf(result.get("failure").textValue());
            this.results.add(t);
        }
    }
}
