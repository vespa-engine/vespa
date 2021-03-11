// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.distribution;

import com.yahoo.collections.BobHash;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.config.content.DistributionConfig;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.document.BucketId;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

public class Distribution {

    private static class Config {
        Config(Group nodeGraph, int redundancy, boolean distributorAutoOwnershipTransferOnWholeGroupDown) {
            this.nodeGraph = nodeGraph;
            this.redundancy = redundancy;
            this.distributorAutoOwnershipTransferOnWholeGroupDown = distributorAutoOwnershipTransferOnWholeGroupDown;
        }

        private final Group nodeGraph;
        private final int redundancy;
        private final boolean distributorAutoOwnershipTransferOnWholeGroupDown;
    }

    private ConfigSubscriber configSub;
    private final AtomicReference<Config> config = new AtomicReference<>(new Config(null, 1, false));

    public Group getRootGroup() {
        return config.getAcquire().nodeGraph;
    }

    public int getRedundancy() {
        return config.getAcquire().redundancy;
    }

    private static int[] getGroupPath(String path) {
        if (path.equals("invalid")) { return new int[0]; }
        StringTokenizer st = new StringTokenizer(path, ".");
        int[] p = new int[st.countTokens()];
        for (int i=0; i<p.length; ++i) {
            p[i] = Integer.valueOf(st.nextToken());
        }
        return p;
    }

    // NOTE: keep in sync with the below
    private ConfigSubscriber.SingleSubscriber<StorDistributionConfig> configSubscriber = config -> {
        try {
            Group root = null;
            for (int i=0; i<config.group().size(); ++i) {
                StorDistributionConfig.Group cg = config.group(i);
                int[] path = new int[0];
                if (root != null) {
                    path = getGroupPath(cg.index());
                }
                boolean isLeafGroup = (cg.nodes().size() > 0);
                Group group;
                int index = (path.length == 0 ? 0 : path[path.length - 1]);
                if (isLeafGroup) {
                    group = new Group(index, cg.name());
                    List<ConfiguredNode> nodes = new ArrayList<>();
                    for (StorDistributionConfig.Group.Nodes node : cg.nodes()) {
                        nodes.add(new ConfiguredNode(node.index(), node.retired()));
                    }
                    group.setNodes(nodes);
                } else {
                    group = new Group(index, cg.name(), new Group.Distribution(cg.partitions(), config.redundancy()));
                }
                group.setCapacity(cg.capacity());
                if (path.length == 0) {
                    root = group;
                } else {
                    Group parent = root;
                    for (int j=0; j<path.length - 1; ++j) {
                        parent = parent.getSubgroups().get(path[j]);
                    }
                    parent.addSubGroup(group);
                }
            }
            if (root == null)
                throw new IllegalStateException("Config does not specify a root group");
            root.calculateDistributionHashValues();
            Distribution.this.config.setRelease(new Config(root, config.redundancy(), config.distributor_auto_ownership_transfer_on_whole_group_down()));
        } catch (ParseException e) {
            throw new IllegalStateException("Failed to parse config", e);
        }
    };

    // TODO jonmv: De-dupe with this.configSubscriber once common config is used
    private void configure(DistributionConfig.Cluster config) {
        try {
            Group root = null;
            for (int i=0; i<config.group().size(); ++i) {
                DistributionConfig.Cluster.Group cg = config.group(i);
                int[] path = new int[0];
                if (root != null) {
                    path = getGroupPath(cg.index());
                }
                boolean isLeafGroup = (cg.nodes().size() > 0);
                Group group;
                int index = (path.length == 0 ? 0 : path[path.length - 1]);
                if (isLeafGroup) {
                    group = new Group(index, cg.name());
                    List<ConfiguredNode> nodes = new ArrayList<>();
                    for (DistributionConfig.Cluster.Group.Nodes node : cg.nodes()) {
                        nodes.add(new ConfiguredNode(node.index(), node.retired()));
                    }
                    group.setNodes(nodes);
                } else {
                    group = new Group(index, cg.name(), new Group.Distribution(cg.partitions(), config.redundancy()));
                }
                group.setCapacity(cg.capacity());
                if (path.length == 0) {
                    root = group;
                } else {
                    Group parent = root;
                    for (int j=0; j<path.length - 1; ++j) {
                        parent = parent.getSubgroups().get(path[j]);
                    }
                    parent.addSubGroup(group);
                }
            }
            if (root == null)
                throw new IllegalStateException("Config does not specify a root group");
            root.calculateDistributionHashValues();
            Distribution.this.config.setRelease(new Config(root, config.redundancy(), true));
        } catch (ParseException e) {
            throw new IllegalStateException("Failed to parse config", e);
        }
    }

    public Distribution(String configId) {
        try {
            configSub = new ConfigSubscriber();
            configSub.subscribe(configSubscriber, StorDistributionConfig.class, configId);
        } catch (Throwable e) {
            close();
            throw e;
        }
    }

    public Distribution(StorDistributionConfig config) {
        configSubscriber.configure(config);
    }

    public Distribution(DistributionConfig.Cluster config) {
        configure(config);
    }

    private static long lastNBits(long value, int n) {
        if (n < 0 || n > 63)
            throw new IllegalArgumentException("n must be in [0, 63], but was " + n);

        return value & ((1L << n) - 1);
    }

    public void close() {
        if (configSub!=null) {
            configSub.close();
            configSub = null;
        }
        configSubscriber = null;
    }

    private int getGroupSeed(BucketId bucket, ClusterState state, Group group) {
        int seed = (int) lastNBits(bucket.getRawId(), state.getDistributionBitCount());
        seed ^= group.getDistributionHash();
        return seed;
    }

    private int getDistributorSeed(BucketId bucket, ClusterState state) {
        return (int) lastNBits(bucket.getRawId(), state.getDistributionBitCount());
    }

    private int getStorageSeed(BucketId bucket, ClusterState state) {
        int seed = (int) lastNBits(bucket.getRawId(), state.getDistributionBitCount());

        if (bucket.getUsedBits() > 33) {
            int usedBits = bucket.getUsedBits() - 1;
            seed ^= lastNBits(bucket.getRawId() >> 32, usedBits - 32) << 6;
        }
        return seed;
    }

    private static class ScoredGroup implements Comparable<ScoredGroup> {
        Group group;
        double score;

        ScoredGroup(Group g, double score) { this.group = g; this.score = score; }

        @Override
        public int compareTo(ScoredGroup o) {
            // Sorts by highest first.
            return Double.compare(o.score, score);
        }
    }

    private static class ScoredNode {
        final double score;
        final int index;

        ScoredNode(int index, double score) {
            this.score = score;
            this.index = index;
        }

        boolean valid() { return index != -1; }

        static ScoredNode makeInvalid() {
            return new ScoredNode(-1, 0.0);
        }
    }

    private static boolean allDistributorsDown(Group g, ClusterState clusterState) {
        if (g.isLeafGroup()) {
            for (ConfiguredNode node : g.getNodes()) {
                NodeState ns = clusterState.getNodeState(new Node(NodeType.DISTRIBUTOR, node.index()));
                if (ns.getState().oneOf("ui")) return false;
            }
        } else {
            for (Group childGroup : g.getSubgroups().values()) {
                if (!allDistributorsDown(childGroup, clusterState)) return false;
            }
        }
        return true;
    }

    private Group getIdealDistributorGroup(boolean distributorAutoOwnershipTransferOnWholeGroupDown,
                                           BucketId bucket, ClusterState clusterState, Group parent, int redundancy) {
        if (parent.isLeafGroup()) {
            return parent;
        }
        int[] redundancyArray = parent.getDistribution().getRedundancyArray(redundancy);
        TreeSet<ScoredGroup> results = new TreeSet<>();
        int seed = getGroupSeed(bucket, clusterState, parent);
        RandomGen random = new RandomGen(seed);
        int currentIndex = 0;
        for(Group g : parent.getSubgroups().values()) {
            while (g.getIndex() < currentIndex++) random.nextDouble();
            double score = random.nextDouble();
            if (Math.abs(g.getCapacity() - 1.0) > 0.0000001) {
                score = Math.pow(score, 1.0 / g.getCapacity());
            }
            results.add(new ScoredGroup(g, score));
        }
        if (distributorAutoOwnershipTransferOnWholeGroupDown) {
            while (!results.isEmpty() && allDistributorsDown(results.first().group, clusterState)) {
                results.remove(results.first());
            }
        }
        if (results.isEmpty()) {
            return null;
        }
        return getIdealDistributorGroup(distributorAutoOwnershipTransferOnWholeGroupDown, bucket, clusterState, results.first().group, redundancyArray[0]);
    }

    private static class ResultGroup implements Comparable<ResultGroup> {
        Group group;
        int redundancy;

        ResultGroup(Group group, int redundancy) {
            this.group = group;
            this.redundancy = redundancy;
        }

        @Override
        public int compareTo(ResultGroup o) {
            return group.compareTo(o.group);
        }
    }

    private void getIdealGroups(BucketId bucketId, ClusterState clusterState, Group parent,
                               int redundancy, List<ResultGroup> results) {
        if (parent.isLeafGroup()) {
            results.add(new ResultGroup(parent, redundancy));
            return;
        }

        int[] redundancyArray = parent.getDistribution().getRedundancyArray(redundancy);

        List<ScoredGroup> tmpResults = new ArrayList<>();
        for (int i = 0; i < redundancyArray.length; ++i) {
            tmpResults.add(new ScoredGroup(null, 0.0));
        }

        int seed = getGroupSeed(bucketId, clusterState, parent);

        RandomGen random = new RandomGen(seed);

        int currentIndex = 0;
        Map<Integer, Group> subGroups = parent.getSubgroups();

        for (Map.Entry<Integer, Group> group : subGroups.entrySet()) {
            while (group.getKey() < currentIndex++) {
                random.nextDouble();
            }

            double score = random.nextDouble();

            if (group.getValue().getCapacity() != 1) {
                score = Math.pow(score, 1.0 / group.getValue().getCapacity());
            }

            if (score > tmpResults.get(tmpResults.size() - 1).score) {
                tmpResults.add(new ScoredGroup(group.getValue(), score));
                Collections.sort(tmpResults);
                tmpResults.remove(tmpResults.size() - 1);
            }
        }

        for (int i = 0; i < tmpResults.size(); ++i) {
            Group group = tmpResults.get(i).group;

            if (group != null) {
                getIdealGroups(bucketId, clusterState, group, redundancyArray[i], results);
            }
        }
    }

    private int getDiskSeed(BucketId bucket, int nodeIndex) {
        // Assumes MODULO_BID for now.

        long currentid = bucket.withoutCountBits();
        byte[] ordered = new byte[8];
        ordered[0] = (byte)(currentid >> (0*8));
        ordered[1] = (byte)(currentid >> (1*8));
        ordered[2] = (byte)(currentid >> (2*8));
        ordered[3] = (byte)(currentid >> (3*8));
        ordered[4] = (byte)(currentid >> (4*8));
        ordered[5] = (byte)(currentid >> (5*8));
        ordered[6] = (byte)(currentid >> (6*8));
        ordered[7] = (byte)(currentid >> (7*8));
        int initval = (1664525 * nodeIndex + 0xdeadbeef);
        return BobHash.hash(ordered, initval);
    }

    List<Integer> getIdealStorageNodes(ClusterState clusterState, BucketId bucket, String upStates) throws TooFewBucketBitsInUseException {
        List<Integer> resultNodes = new ArrayList<>();

        // If bucket is split less than distribution bit, we cannot distribute
        // it. Different nodes own various parts of the bucket.
        if (bucket.getUsedBits() < clusterState.getDistributionBitCount()) {
            String msg = "Cannot get ideal state for bucket " + bucket + " using "
                    + bucket.getUsedBits() + " bits when cluster uses "
                    + clusterState.getDistributionBitCount() + " distribution bits.";
            throw new TooFewBucketBitsInUseException(msg);
        }

        // Find what hierarchical groups we should have copies in
        List<ResultGroup> groupDistribution = new ArrayList<>();

        Config cfg = config.getAcquire();
        getIdealGroups(bucket, clusterState, cfg.nodeGraph, cfg.redundancy, groupDistribution);

        int seed = getStorageSeed(bucket, clusterState);

        RandomGen random = new RandomGen(seed);
        int randomIndex = 0;
        for (ResultGroup group : groupDistribution) {
            int redundancy = group.redundancy;
            Collection<ConfiguredNode> nodes = group.group.getNodes();

            // Create temporary place to hold results. Use double linked list
            // for cheap access to back(). Stuff in redundancy fake entries to
            // avoid needing to check size during iteration.
            LinkedList<ScoredNode> tmpResults = new LinkedList<>();
            for (int i = 0; i < redundancy; ++i) {
                tmpResults.add(ScoredNode.makeInvalid());
            }

            for (ConfiguredNode configuredNode : nodes) {
                NodeState nodeState = clusterState.getNodeState(new Node(NodeType.STORAGE, configuredNode.index()));
                if (!nodeState.getState().oneOf(upStates)) {
                    continue;
                }

                // Get the score from the random number generator. Make sure we
                // pick correct random number. Optimize for the case where we
                // pick in rising order.
                if (configuredNode.index() != randomIndex) {
                    if (configuredNode.index() < randomIndex) {
                        random.setSeed(seed);
                        randomIndex = 0;
                    }

                    for (int k = randomIndex; k < configuredNode.index(); ++k) {
                        random.nextDouble();
                    }

                    randomIndex = configuredNode.index();
                }

                double score = random.nextDouble();
                ++randomIndex;
                if (nodeState.getCapacity() != 1.0) {
                    score = Math.pow(score, 1.0 / nodeState.getCapacity());
                }
                if (score > tmpResults.getLast().score) {
                    for (int i = 0; i < tmpResults.size(); ++i) {
                        if (score > tmpResults.get(i).score) {
                            tmpResults.add(i, new ScoredNode(configuredNode.index(), score));
                            break;
                        }
                    }
                    tmpResults.removeLast();
                }
            }

            for (ScoredNode node : tmpResults) {
                if (node.valid()) {
                    resultNodes.add(node.index);
                }
            }
        }

        return resultNodes;
    }

    public static class TooFewBucketBitsInUseException extends Exception {
        TooFewBucketBitsInUseException(String message) {
            super(message);
        }
    }

    public static class NoDistributorsAvailableException extends Exception {
        NoDistributorsAvailableException(String message) {
            super(message);
        }
    }

    public int getIdealDistributorNode(ClusterState state, BucketId bucket, String upStates) throws TooFewBucketBitsInUseException, NoDistributorsAvailableException {
        if (bucket.getUsedBits() < state.getDistributionBitCount()) {
            throw new TooFewBucketBitsInUseException("Cannot get ideal state for bucket " + bucket + " using " + bucket.getUsedBits()
                    + " bits when cluster uses " + state.getDistributionBitCount() + " distribution bits.");
        }

        Config cfg = config.getAcquire();
        Group idealGroup = getIdealDistributorGroup(cfg.distributorAutoOwnershipTransferOnWholeGroupDown, bucket, state, cfg.nodeGraph, cfg.redundancy);
        if (idealGroup == null) {
            throw new NoDistributorsAvailableException("No distributors available in cluster state version " + state.getVersion());
        }
        int seed = getDistributorSeed(bucket, state);
        RandomGen random = new RandomGen(seed);
        int randomIndex = 0;
        List<ConfiguredNode> configuredNodes = idealGroup.getNodes();
        ScoredNode node = ScoredNode.makeInvalid();
        for (ConfiguredNode configuredNode : configuredNodes) {
            NodeState nodeState = state.getNodeState(new Node(NodeType.DISTRIBUTOR, configuredNode.index()));
            if (!nodeState.getState().oneOf(upStates)) continue;
            if (configuredNode.index() != randomIndex) {
                if (configuredNode.index() < randomIndex) {
                    random.setSeed(seed);
                    randomIndex = 0;
                }
                for (int k=randomIndex; k < configuredNode.index(); ++k) {
                    random.nextDouble();
                }
                randomIndex = configuredNode.index();
            }
            double score = random.nextDouble();
            ++randomIndex;
            if (Math.abs(nodeState.getCapacity() - 1.0) > 0.0000001) {
                score = Math.pow(score, 1.0 / nodeState.getCapacity());
            }
            if (score > node.score) {
                node = new ScoredNode(configuredNode.index(), score);
            }
        }
        if (!node.valid()) {
            throw new NoDistributorsAvailableException(
                    "No available distributors in any of the given upstates '"
                    + upStates + "'.");
        }
        return node.index;
    }

    private boolean visitGroups(GroupVisitor visitor, Map<Integer, Group> groups) {
        for (Group g : groups.values()) {
            if (!visitor.visitGroup(g)) return false;
            if (!g.isLeafGroup()) {
                if (!visitGroups(visitor, g.getSubgroups())) {
                    return false;
                }
            }
        }
        return true;
    }

    public void visitGroups(GroupVisitor visitor) {
        Map<Integer, Group> groups = new TreeMap<>();
        Group nodeGraph = config.getAcquire().nodeGraph;
        groups.put(nodeGraph.getIndex(), nodeGraph);
        visitGroups(visitor, groups);
    }

    public Set<ConfiguredNode> getNodes() {
        final Set<ConfiguredNode> nodes = new HashSet<>();
        GroupVisitor visitor = new GroupVisitor() {
            @Override
            public boolean visitGroup(Group g) {
                if (g.isLeafGroup()) {
                    nodes.addAll(g.getNodes());
                }
                return true;
            }
        };
        visitGroups(visitor);
        return nodes;
    }

    public static String getDefaultDistributionConfig(int redundancy, int nodeCount) {
        return getDefaultDistributionConfig(redundancy, nodeCount, StorDistributionConfig.Disk_distribution.MODULO_BID);
    }

    public static String getDefaultDistributionConfig(int redundancy, int nodeCount, StorDistributionConfig.Disk_distribution.Enum diskDistribution) {
        StringBuilder sb = new StringBuilder();
        sb.append("raw:redundancy ").append(redundancy).append("\n")
          .append("group[1]\n")
          .append("group[0].index \"invalid\"\n")
          .append("group[0].name \"invalid\"\n")
          .append("group[0].partitions \"*\"\n")
          .append("group[0].nodes[").append(nodeCount).append("]\n");
        for (int i=0; i<nodeCount; ++i) {
            sb.append("group[0].nodes[").append(i).append("].index ").append(i).append("\n");
        }
        sb.append("disk_distribution ").append(diskDistribution.toString()).append("\n");
        return sb.toString();
    }

    public static String getSimpleGroupConfig(int redundancy, int nodeCount) {
        return getSimpleGroupConfig(redundancy, nodeCount, StorDistributionConfig.Disk_distribution.Enum.MODULO_BID);
    }

    private static String getSimpleGroupConfig(int redundancy, int nodeCount, StorDistributionConfig.Disk_distribution.Enum diskDistribution) {
        StringBuilder sb = new StringBuilder();
        sb.append("raw:redundancy ").append(redundancy).append("\n").append("group[4]\n");

        int group = 0;
        sb.append("group[" + group + "].index \"invalid\"\n")
          .append("group[" + group + "].name \"invalid\"\n")
          .append("group[" + group + "].partitions \"1|*\"\n");

        ++group;
        sb.append("group[" + group + "].index \"0\"\n")
          .append("group[" + group + "].name \"east\"\n")
          .append("group[" + group + "].partitions \"*\"\n");

        ++group;
        sb.append("group[" + group + "].index \"0.0\"\n")
          .append("group[" + group + "].name \"g1\"\n")
          .append("group[" + group + "].partitions \"*\"\n")
          .append("group[" + group + "].nodes[").append((nodeCount + 1) / 2).append("]\n");
        for (int i=0; i<nodeCount; i += 2) {
            sb.append("group[" + group + "].nodes[").append(i / 2).append("].index ").append(i).append("\n");
        }

        ++group;
        sb.append("group[" + group + "].index \"0.1\"\n")
          .append("group[" + group + "].name \"g2\"\n")
          .append("group[" + group + "].partitions \"*\"\n")
          .append("group[" + group + "].nodes[").append(nodeCount / 2).append("]\n");
        for (int i=1; i<nodeCount; i += 2) {
            sb.append("group[" + group + "].nodes[").append(i / 2).append("].index ").append(i).append("\n");
        }
        sb.append("disk_distribution ").append(diskDistribution.toString()).append("\n");
        return sb.toString();
    }

}
