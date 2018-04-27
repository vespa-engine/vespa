// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test.storagepolicy;

import com.yahoo.document.BucketId;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.DocumentId;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.StoragePolicy;
import com.yahoo.messagebus.routing.RoutingNode;
import com.yahoo.vdslib.distribution.RandomGen;
import com.yahoo.vdslib.state.*;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class Simulator extends StoragePolicyTestEnvironment {

    enum FailureType {
        TRANSIENT_ERROR,
        FATAL_ERROR,
        OLD_CLUSTER_STATE,
        RESET_CLUSTER_STATE,
        RESET_CLUSTER_STATE_NO_GOOD_NODES,
        NODE_NOT_IN_SLOBROK
    };
    private Integer getIdealTarget(String idString, String clusterState) {
        DocumentId did = new DocumentId(idString);
        BucketIdFactory factory = new BucketIdFactory();
        BucketId bid = factory.getBucketId(did);
        try{
            return policyFactory.getLastParameters().createDistribution(null).getIdealDistributorNode(new ClusterState(clusterState), bid, "uim");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public class BadNode {
        private int index;
        private double failureRate = 1.0;
        private FailureType failureType;
        private ClusterState badClusterState;
        private boolean downInCurrentState = false;

        public BadNode(int index, FailureType type) {
            this.index = index;
            this.failureType = type;
        }

        public BadNode setFailureRate(double rate) { failureRate = rate; return this;  }
        public BadNode setBadClusterState(ClusterState state) {
            badClusterState = state;
            if (debug) System.err.println("Setting bad cluster state in distributor " + index + ": " + state);
            return this;
        }
        public BadNode setDownInCurrentState() { downInCurrentState = true; return this; }

        public int getIndex() { return index; }
        public FailureType getFailureType() { return failureType; }
        public double getFailureRate() { return failureRate; }
        public ClusterState getBadClusterState() { return badClusterState; }
        public boolean isSetDownInCurrentState() { return downInCurrentState; }
    }

    public class PersistentFailureTestParameters {
        private ClusterState initialClusterState;
        private ClusterState currentClusterState;
        private int totalRequests = 200;
        private int parallellRequests = 10;
        private Map<Integer, BadNode> badnodes = new TreeMap<Integer, BadNode>();
        private boolean newNode = false;

        public PersistentFailureTestParameters() {
            try{
                initialClusterState = new ClusterState("version:2 bits:16 distributor:9");
                currentClusterState = initialClusterState.clone();
                currentClusterState.setVersion(3);
            } catch (Exception e) { throw new RuntimeException(e); }
        }

        public PersistentFailureTestParameters setTotalRequests(int count) { totalRequests = count; return this; }
        public PersistentFailureTestParameters setParallellRequests(int count) { parallellRequests = count; return this; }
        public PersistentFailureTestParameters addBadNode(BadNode node) { badnodes.put(node.getIndex(), node); return this; }
        public PersistentFailureTestParameters newNodeAdded() {
            currentClusterState.setNodeState(new Node(NodeType.DISTRIBUTOR, 9), new NodeState(NodeType.DISTRIBUTOR, State.UP));
            newNode = true;
            return this;
        }

        public void validate() {
            // To simplify looping, ensure we do complete loops
            assertTrue(totalRequests % parallellRequests == 0);
            // Node that in some tests are used as new node up, cannot be a bad node
            assertTrue(!badnodes.containsKey(9));
            for (BadNode node : badnodes.values()) {
                ClusterState badClusterState = currentClusterState.clone();
                int nodesToSetDown = 0;
                if (node.getFailureType() == FailureType.OLD_CLUSTER_STATE) {
                    badClusterState.setVersion(1);
                    nodesToSetDown = 4;
                } else if (node.getFailureType() == FailureType.RESET_CLUSTER_STATE) {
                    badClusterState.setVersion(5);
                    nodesToSetDown = 4;
                } else if (node.getFailureType() == FailureType.RESET_CLUSTER_STATE_NO_GOOD_NODES) {
                    badClusterState.setVersion(5);
                    nodesToSetDown = -1;
                } else {
                    badClusterState = null;
                }
                if (badClusterState != null) {
                    int setDown = 0;
                    for (int i=0; i<10; ++i) {
                        if (nodesToSetDown != -1 && setDown >= nodesToSetDown) break;
                        if (badnodes.containsKey(i)) continue;
                        badClusterState.setNodeState(new Node(NodeType.DISTRIBUTOR, i), new NodeState(NodeType.DISTRIBUTOR, State.DOWN));
                        ++setDown;
                    }
                    if (nodesToSetDown > 0 && nodesToSetDown != setDown) throw new IllegalStateException("Failed to set down " + nodesToSetDown + " nodes");
                    node.setBadClusterState(badClusterState);
                }
                if (node.isSetDownInCurrentState()) {
                    currentClusterState.setNodeState(new Node(NodeType.DISTRIBUTOR, node.getIndex()), new NodeState(NodeType.DISTRIBUTOR, State.DOWN));
                }
            }
            if (debug) System.err.println("Using initial state " + initialClusterState);
            if (debug) System.err.println("Using current state " + currentClusterState);
        }

        public int getTotalRequests() { return totalRequests; }
        public int getParallellRequests() { return parallellRequests; }
        public Map<Integer, BadNode> getBadNodes() { return badnodes; }
        public boolean isNewNodeAdded() { return newNode; }
        public ClusterState getInitialClusterState() { return initialClusterState; }
        public ClusterState getCurrentClusterState(Integer distributor) {
            if (distributor != null && badnodes.containsKey(distributor) && badnodes.get(distributor).getBadClusterState() != null) {
                return badnodes.get(distributor).getBadClusterState();
            }
            return currentClusterState;
        }
    }
    public void runSimulation(String expected, PersistentFailureTestParameters params) {
        params.validate();
        // Set nodes in slobrok
        setClusterNodes(new int[]{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        for (BadNode node : params.getBadNodes().values()) {
            if (node.getFailureType() == FailureType.NODE_NOT_IN_SLOBROK) removeNode(node.getIndex());
        }
        {
            RoutingNode target = select();
            replyWrongDistribution(target, "foo", null, params.getInitialClusterState().toString());
        }
        RandomGen randomizer = new RandomGen(432121);
        int correctnode[] = new int[2],
                wrongnode[] = new int[2],
                failed[] = new int[2],
                worked[] = new int[2],
                downnode[] = new int[2];
        for (int step = 0, steps = (params.getTotalRequests() / params.getParallellRequests()); step < steps; ++step) {
            int half = (step < steps / 2 ? 0 : 1);
            if (debug) System.err.println("Starting step " + step + " in half " + half);
            String docId[] = new String[params.getParallellRequests()];
            RoutingNode targets[] = new RoutingNode[params.getParallellRequests()];
            for (int i=0; i<params.getParallellRequests(); ++i) {
                docId[i] = "doc:ns:" + (step * params.getParallellRequests() + i);
                frame.setMessage(createMessage(docId[i]));
                targets[i] = select();
            }
            for (int i=0; i<params.getParallellRequests(); ++i) {
                RoutingNode target = targets[i];
                int index = getAddress(target).getSecond();
                if (!params.getCurrentClusterState(null).getNodeState(new Node(NodeType.DISTRIBUTOR, index)).getState().oneOf(StoragePolicy.owningBucketStates)) {
                    ++downnode[half];
                }
                BadNode badNode = params.getBadNodes().get(index);
                if (getAddress(target).getSecond() == getIdealTarget(docId[i], params.getCurrentClusterState(null).toString())) {
                    ++correctnode[half];
                } else {
                    ++wrongnode[half];
                }
                if (badNode != null && randomizer.nextDouble() < badNode.getFailureRate()) {
                    ++failed[half];
                    switch (badNode.getFailureType()) {
                        case TRANSIENT_ERROR: replyError(target, new com.yahoo.messagebus.Error(DocumentProtocol.ERROR_BUSY, "Transient error")); break;
                        case FATAL_ERROR: replyError(target, new com.yahoo.messagebus.Error(DocumentProtocol.ERROR_UNPARSEABLE, "Fatal error")); break;
                        case OLD_CLUSTER_STATE:
                        case RESET_CLUSTER_STATE:
                        case RESET_CLUSTER_STATE_NO_GOOD_NODES: replyWrongDistribution(target, "foo", null, params.getCurrentClusterState(index).toString()); break;
                        case NODE_NOT_IN_SLOBROK: throw new IllegalStateException("This point in code should not be reachable");
                    }
                } else {
                    ++worked[half];
                    boolean correctTarget = (getAddress(target).getSecond() == getIdealTarget(docId[i], params.getCurrentClusterState(index).toString()));
                    if (correctTarget) {
                        replyOk(target);
                    } else {
                        replyWrongDistribution(target, "foo", null, params.getCurrentClusterState(index).toString());
                    }
                }
            }
        }
        StringBuilder actual = new StringBuilder();
        String result[][] = new String[2][];
        for (int i=0; i<2; ++i) {
            actual.append(i == 0 ? "First " : " Last ")
                    .append("correctnode ").append(correctnode[i])
                    .append(", wrongnode ").append(wrongnode[i])
                    .append(", downnode ").append(downnode[i])
                    .append(", worked ").append(worked[i])
                    .append(", failed ").append(failed[i]);
        }
        if (!Pattern.matches(expected, actual.toString())) {
            assertEquals(expected, actual.toString());
        }
    }

}
