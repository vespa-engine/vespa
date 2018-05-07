// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.distribution;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.document.BucketId;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DistributionTestFactory extends CrossPlatformTestFactory {

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
        private List<Integer> disks;
        private Failure failure;

        public Test(BucketId bucket) {
            this.bucket = bucket;
            nodes = new ArrayList<>();
            disks = new ArrayList<>();
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
            return java.util.Objects.hash(bucket, nodes, disks);
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
        public List<Integer> getDisks() {
            return disks;
        }
        public Integer getDiskForNode(int node) {
            for (int i=0; i<nodes.size(); ++i) {
                if (nodes.get(i) == node) return disks.get(i);
            }
            fail("Node " + node + " is not in use: " + toString());
            throw new IllegalStateException("Control should not reach here");
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
                    NodeState ns = state.getNodeState(new Node(nodeType, i));
                    if (ns.getDiskCount() != 0) {
                        t.disks.add(d.getIdealDisk(ns, i, bucket));
                    } else {
                        t.disks.add(-1);
                    }
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
        JSONObject test = new JSONObject()
                .put("cluster-state", state.toString())
                .put("distribution", new StorDistributionConfig(distributionConfig).toString())
                .put("node-type", nodeType.toString())
                .put("redundancy", redundancy)
                .put("node-count", nodeCount)
                .put("up-states", upStates);
        JSONArray results = new JSONArray();
        for(Test t : this.results) {
            JSONArray nodes = new JSONArray();
            for (int i : t.nodes) {
                nodes.put(i);
            }
            JSONArray disks = new JSONArray();
            for (int i : t.disks) {
                nodes.put(i);
            }
            JSONObject testResult = new JSONObject()
                    .put("bucket", Long.toHexString(t.bucket.getId()))
                    .put("nodes", nodes)
                    .put("failure", t.failure.toString());
            if (nodeType == NodeType.STORAGE) {
                testResult.put("disks", disks);
            }
            results.put(testResult);
        }
        test.put("result", results);
        return test.toString(2);
    }

    public void parse(String serialized) throws Exception {
        JSONObject json = new JSONObject(serialized);
        upStates = json.getString("up-states");
        nodeCount = json.getInt("redundancy");
        redundancy = json.getInt("redundancy");
        state = new ClusterState(json.getString("cluster-state"));
        distributionConfig = deserializeConfig(json.getString("distribution"));
        nodeType = NodeType.get(json.getString("node-type"));
        JSONArray results = json.getJSONArray("result");
        for (int i=0; i<results.length(); ++i) {
            JSONObject result = results.getJSONObject(i);
            Test t = new Test(new BucketId(Long.parseLong(result.getString("bucket"), 16)));
            {
                JSONArray nodes = result.getJSONArray("nodes");
                for (int j=0; j<nodes.length(); ++j) {
                    t.nodes.add(nodes.getInt(j));
                }
            }
            if (nodeType == NodeType.STORAGE) {
                JSONArray disks = result.getJSONArray("disks");
                for (int j=0; j<disks.length(); ++j) {
                    t.disks.add(disks.getInt(j));
                }
            }
            t.failure = Failure.valueOf(result.getString("failure"));
            this.results.add(t);
        }
    }
}
