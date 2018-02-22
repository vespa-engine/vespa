// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.state;
import com.yahoo.text.StringUtilities;

import java.text.ParseException;
import java.util.*;

/**
 * Be careful about changing this class, as it mirrors the ClusterState in C++.
 * Please update both if you need to change anything.
 */
public class ClusterState implements Cloneable {

    private static final NodeState DEFAULT_STORAGE_UP_NODE_STATE = new NodeState(NodeType.STORAGE, State.UP);
    private static final NodeState DEFAULT_DISTRIBUTOR_UP_NODE_STATE = new NodeState(NodeType.DISTRIBUTOR, State.UP);

    private int version = 0;
    private State state = State.DOWN;
    // nodeStates maps each of the non-up nodes that have an index <= the node count for its type.
    private Map<Node, NodeState> nodeStates = new TreeMap<>();

    // TODO: Change to one count for distributor and one for storage, rather than an array
    // TODO: RenameFunction, this is not the highest node count but the highest index
    private ArrayList<Integer> nodeCount = new ArrayList<>(2);

    private String description = "";
    private int distributionBits = 16;
    private boolean official = false;

    public ClusterState(String serialized) throws ParseException {
        nodeCount.add(0);
        nodeCount.add(0);
        deserialize(serialized);
    }

    /**
     * Parse a given cluster state string into a returned ClusterState instance, wrapping any
     * parse exceptions in a RuntimeException.
     */
    public static ClusterState stateFromString(final String stateStr) {
        try {
            return new ClusterState(stateStr);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ClusterState emptyState() {
        return stateFromString("");
    }

    public ClusterState clone() {
        try{
            ClusterState state = (ClusterState) super.clone();
            state.nodeStates = new TreeMap<>();
            for (Map.Entry<Node, NodeState> entry : nodeStates.entrySet()) {
                state.nodeStates.put(entry.getKey(), entry.getValue().clone());
            }
            state.nodeCount = new ArrayList<>(2);
            state.nodeCount.add(nodeCount.get(0));
            state.nodeCount.add(nodeCount.get(1));
            return state;
        } catch (CloneNotSupportedException e) {
            assert(false); // Should never happen
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ClusterState)) { return false; }
        ClusterState other = (ClusterState) o;
        if (version != other.version
            || !state.equals(other.state)
            || distributionBits != other.distributionBits
            || !nodeCount.equals(other.nodeCount)
            || !nodeStates.equals(other.nodeStates))
        {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(version, state, distributionBits, nodeCount, nodeStates);
    }

    @FunctionalInterface
    private interface NodeStateCmp {
        boolean similar(NodeType nodeType, NodeState lhs, NodeState rhs);
    }

    public boolean similarTo(Object o) {
        if (!(o instanceof ClusterState)) { return false; }
        final ClusterState other = (ClusterState) o;

        return similarToImpl(other, this::normalizedNodeStateSimilarTo);
    }

    public boolean similarToIgnoringInitProgress(final ClusterState other) {
        return similarToImpl(other, this::normalizedNodeStateSimilarToIgnoringInitProgress);
    }

    private boolean similarToImpl(final ClusterState other, final NodeStateCmp nodeStateCmp) {
        if (other == this) {
            return true; // We're definitely similar to ourselves.
        }
        // Two cluster states are considered similar if they are both down. When clusters
        // are down, their individual node states do not matter to ideal state computations
        // and content nodes therefore do not need to observe them.
        if (state.equals(State.DOWN) && other.state.equals(State.DOWN)) {
            return true;
        }
        if (!metaInformationSimilarTo(other)) {
            return false;
        }
        // TODO verify behavior of C++ impl against this
        for (Node node : unionNodeSetWith(other.nodeStates.keySet())) {
            final NodeState lhs = nodeStates.get(node);
            final NodeState rhs = other.nodeStates.get(node);
            if (!nodeStateCmp.similar(node.getType(), lhs, rhs)) {
                return false;
            }
        }
        return true;
    }

    private Set<Node> unionNodeSetWith(final Set<Node> otherNodes) {
        final Set<Node> unionNodeSet = new TreeSet<Node>(nodeStates.keySet());
        unionNodeSet.addAll(otherNodes);
        return unionNodeSet;
    }

    private boolean metaInformationSimilarTo(final ClusterState other) {
        if (version != other.version || !state.equals(other.state)) {
            return false;
        }
        if (distributionBits != other.distributionBits) {
            return false;
        }
        return nodeCount.equals(other.nodeCount);
    }

    private boolean normalizedNodeStateSimilarTo(final NodeType nodeType, final NodeState lhs, final NodeState rhs) {
        final NodeState lhsNormalized = (lhs != null ? lhs : defaultUpNodeState(nodeType));
        final NodeState rhsNormalized = (rhs != null ? rhs : defaultUpNodeState(nodeType));

        return lhsNormalized.similarTo(rhsNormalized);
    }

    private boolean normalizedNodeStateSimilarToIgnoringInitProgress(
            final NodeType nodeType, final NodeState lhs, final NodeState rhs)
    {
        final NodeState lhsNormalized = (lhs != null ? lhs : defaultUpNodeState(nodeType));
        final NodeState rhsNormalized = (rhs != null ? rhs : defaultUpNodeState(nodeType));

        return lhsNormalized.similarToIgnoringInitProgress(rhsNormalized);
    }

    private static NodeState defaultUpNodeState(final NodeType nodeType) {
        return nodeType == NodeType.STORAGE
                ? DEFAULT_STORAGE_UP_NODE_STATE
                : DEFAULT_DISTRIBUTOR_UP_NODE_STATE;
    }

    /**
     * Fleet controller marks states that are actually sent out to nodes as official states. Only fleetcontroller
     * should set this to official, and only just before sending it out. This state is currently not serialized with
     * the system state, but only used internally in the fleetcontroller. Might be useful client side though, where
     * clients modify states to mark nodes down that they cannot speak with.
     */
    public void setOfficial(boolean official) { this.official = official; }
    /** Whether this system state is an unmodified version of an official system state. */
    public boolean isOfficial() { return official; }

    /** Used during deserialization */
    private class NodeData {

        boolean empty = true;
        Node node = new Node(NodeType.STORAGE, 0);
        StringBuilder sb = new StringBuilder();

        public void addNodeState() throws ParseException {
            if (!empty) {
                NodeState ns = NodeState.deserialize(node.getType(), sb.toString());
                if (!ns.equals(defaultUpNodeState(node.getType()))) {
                    nodeStates.put(node, ns);
                }
                if (nodeCount.get(node.getType().ordinal()) <= node.getIndex()) {
                    nodeCount.set(node.getType().ordinal(), node.getIndex() + 1);
                }
            }
            empty = true;
            sb = new StringBuilder();
        }
    }

    private void deserialize(String serialized) throws ParseException {
        official = false;
        StringTokenizer st = new StringTokenizer(serialized, " \t\n\f\r", false);
        NodeData nodeData = new NodeData();
        String lastAbsolutePath = "";
        state = State.UP;
        while (st.hasMoreTokens()) {
            String token = st.nextToken();

            int index = token.indexOf(':');
            if (index < 0) {
                throw new ParseException("Token " + token + " does not contain ':': " + serialized, 0);
            }
            String key = token.substring(0, index);
            String value = token.substring(index + 1);
            if (key.length() > 0 && key.charAt(0) == '.') {
                if (lastAbsolutePath.equals("")) {
                    throw new ParseException("The first path in system state string needs to be absolute, in state: " + serialized, 0);
                }
                key = lastAbsolutePath + key;
            } else {
                lastAbsolutePath = key;
            }
            if (key.length() > 0) switch (key.charAt(0)) {
                case 'c':
                    if (key.equals("cluster")) {
                        setClusterState(State.get(value));
                        continue;
                    }
                    break;
            case 'b':
                if (key.equals("bits")) {
                    distributionBits = Integer.parseInt(value);
                    continue;
                }
                break;
            case 'v':
                if (key.equals("version")) {
                    Integer version;
                    try{
                        version = Integer.valueOf(value);
                    } catch (Exception e) {
                        throw new ParseException("Illegal version '" + value + "'. Must be an integer, in state: " + serialized, 0);
                        }
                    setVersion(version);
                    continue;
                }
                break;
            case 'm':
                if (key.length() > 1) break;
                setDescription(StringUtilities.unescape(value));
                continue;
            case 'd':
            case 's':
                NodeType nodeType = null;
                int dot = key.indexOf('.');
                String type = (dot < 0 ? key : key.substring(0, dot));
                if (type.equals("storage")) {
                    nodeType = NodeType.STORAGE;
                } else if (type.equals("distributor")) {
                    nodeType = NodeType.DISTRIBUTOR;
                }
                if (nodeType == null) break;
                if (dot < 0) {
                    int nodeCount;
                    try{
                        nodeCount = Integer.valueOf(value);
                    } catch (Exception e) {
                        throw new ParseException("Illegal node count '" + value + "' in state: " + serialized, 0);
                    }
                    if (nodeCount > this.nodeCount.get(nodeType.ordinal())) {
                        this.nodeCount.set(nodeType.ordinal(), nodeCount);
                    }
                    continue;
                }
                int dot2 = key.indexOf('.', dot + 1);
                Node node;
                if (dot2 < 0) {
                    node = new Node(nodeType, Integer.valueOf(key.substring(dot + 1)));
                } else {
                    node = new Node(nodeType, Integer.valueOf(key.substring(dot + 1, dot2)));
                }
                if (node.getIndex() >= this.nodeCount.get(nodeType.ordinal())) {
                    throw new ParseException("Cannot index " + nodeType + " node " + node.getIndex() + " of " + this.nodeCount.get(nodeType.ordinal()) + " in state: " + serialized, 0);
                }
                if (!nodeData.node.equals(node)) {
                    nodeData.addNodeState();
                }
                if (dot2 < 0) {
                    break; // No default key for nodeStates.
                } else {
                    nodeData.sb.append(' ').append(key.substring(dot2 + 1)).append(':').append(value);
                }
                nodeData.node = node;
                nodeData.empty = false;
                continue;
            default:
                break;
            }
            // Ignore unknown nodeStates
        }
        nodeData.addNodeState();
        removeLastNodesDownWithoutReason();
    }

    public String getTextualDifference(ClusterState other) {
        return getDiff(other).toString();
    }
    public String getHtmlDifference(ClusterState other) {
        return getDiff(other).toHtml();
    }

    public Diff getDiff(ClusterState other) {
        Diff diff = new Diff();

        if (version != other.version) {
            diff.add(new Diff.Entry("version", version, other.version));
        }
        if (!state.equals(other.state)) {
            diff.add(new Diff.Entry("cluster", state, other.state));
        }
        if (distributionBits != other.distributionBits) {
            diff.add(new Diff.Entry("bits", distributionBits, other.distributionBits));
        }
        if (official != other.official) {
            diff.add(new Diff.Entry("official", official, other.official));
        }
        for (NodeType type : NodeType.getTypes()) {
            Diff typeDiff = new Diff();
            int maxCount = Math.max(getNodeCount(type), other.getNodeCount(type));
            for (int i = 0; i < maxCount; i++) {
                Node n = new Node(type, i);
                Diff d = getNodeState(n).getDiff(other.getNodeState(n));
                if (d.differs()) {
                    typeDiff.add(new Diff.Entry(i, d));
                }
           }
           if (typeDiff.differs()) {
               diff.add(new Diff.Entry(type, typeDiff).splitLine());
           }
        }
        return diff;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        official = false;
        this.version = version;
    }

    public int getDistributionBitCount() { return distributionBits; }
    public void setDistributionBits(int bits) { distributionBits = bits; }

    /**
     * Returns the state of this cluster state. In particular, it does not return the cluster state,
     * no matter what the function name says.
     */
    public State getClusterState() { return state; }

    /**
     * Sets the state of this cluster state. In particular, it does not set the cluster state,
     * no matter what the function name says.
     */
    public void setClusterState(State s) {
        if (!s.validClusterState()) {
            throw new IllegalArgumentException("Illegal cluster state " + s);
        }
        state = s;
    }

    /**
     * Take the distributor nodes as an example. Let X be the highest index of
     * the distributor nodes added through setNodeState(). Let Y be the number
     * of suffix nodes for which the state is down and without description.
     * E.g. if node X is down and without description, but nodex X-1 is up, then Y is 1.
     * The node count for distributors is then X + 1 - Y.
     */
    public int getNodeCount(NodeType type) { return nodeCount.get(type.ordinal()); }

    /**
     * Returns the state of a node.
     * If the node is not known this returns a node in the state UP (never null) if it has lower index than the max
     * and DOWN otherwise.
     */
    public NodeState getNodeState(Node node) {
        if (node.getIndex() >= nodeCount.get(node.getType().ordinal()))
            return new NodeState(node.getType(), State.DOWN);
        return nodeStates.getOrDefault(node, new NodeState(node.getType(), State.UP));
    }

    /**
     * Set the node state of the given node.
     *
     * Automatically adjusts number of nodes of that given type if out of range of current nodes seen.
     */
    public void setNodeState(Node node, NodeState newState) {
        newState.verifyValidInSystemState(node.getType());
        if (node.getIndex() >= nodeCount.get(node.getType().ordinal())) {
            for (int i= nodeCount.get(node.getType().ordinal()); i<node.getIndex(); ++i) {
                nodeStates.put(new Node(node.getType(), i), new NodeState(node.getType(), State.DOWN));
            }
            nodeCount.set(node.getType().ordinal(), node.getIndex() + 1);
        }
        if (newState.equals(new NodeState(node.getType(), State.UP))) {
            nodeStates.remove(node);
        } else {
            nodeStates.put(node, newState);
        }
        if (newState.getState().equals(State.DOWN)) {
            // We might be setting the last node down, so we can remove some states
            removeLastNodesDownWithoutReason();
        }
    }

    private void removeLastNodesDownWithoutReason() {
        for (NodeType nodeType : NodeType.values()) {
            for (int index = nodeCount.get(nodeType.ordinal()) - 1; index >= 0; --index) {
                Node node = new Node(nodeType, index);
                NodeState nodeState = nodeStates.get(node);
                if (nodeState == null) break; // Node not existing is up
                if ( ! nodeState.getState().equals(State.DOWN)) break; // Node not down can not be removed
                if (nodeState.hasDescription()) break; // Node have reason to be down. Don't remove node as we will forget reason
                nodeStates.remove(node);
                nodeCount.set(nodeType.ordinal(), node.getIndex());
            }
        }
    }

    public String getDescription() { return description; }

    public void setDescription(String description) {
        this.description = description;
    }

    /** Returns the serialized form of this cluster state */
    // TODO: Don't rely on toString for that
    @Override
    public String toString() { return toString(false); }

    public String toString(boolean verbose) {
        StringBuilder sb = new StringBuilder();

        if (version != 0) {
            sb.append(" version:").append(version);
        }

        if (!state.equals(State.UP)) {
            sb.append(" cluster:").append(state.serialize());
        }

        if (distributionBits != 16) {
            sb.append(" bits:").append(distributionBits);
        }

        int distributorNodeCount = getNodeCount(NodeType.DISTRIBUTOR);
        int storageNodeCount = getNodeCount(NodeType.STORAGE);
        // If not printing verbose, we're not printing descriptions, so we can remove tailing nodes that are down that has descriptions too
        if (!verbose) {
            while (distributorNodeCount > 0 && getNodeState(new Node(NodeType.DISTRIBUTOR, distributorNodeCount - 1)).getState().equals(State.DOWN)) --distributorNodeCount;
            while (storageNodeCount > 0 && getNodeState(new Node(NodeType.STORAGE, storageNodeCount - 1)).getState().equals(State.DOWN)) --storageNodeCount;
        }
        if (distributorNodeCount > 0){
            sb.append(" distributor:").append(distributorNodeCount);
            for (Map.Entry<Node, NodeState> entry : nodeStates.entrySet()) {
                if (entry.getKey().getType().equals(NodeType.DISTRIBUTOR) && entry.getKey().getIndex() < distributorNodeCount) {
                    String nodeState = entry.getValue().serialize(entry.getKey().getIndex(), verbose);
                    if (!nodeState.isEmpty()) {
                        sb.append(' ').append(nodeState);
                    }
                }
            }
        }
        if (storageNodeCount > 0){
            sb.append(" storage:").append(storageNodeCount);
            for (Map.Entry<Node, NodeState> entry : nodeStates.entrySet()) {
                if (entry.getKey().getType().equals(NodeType.STORAGE) && entry.getKey().getIndex() < storageNodeCount) {
                    String nodeState = entry.getValue().serialize(entry.getKey().getIndex(), verbose);
                    if (!nodeState.isEmpty()) {
                        sb.append(' ').append(nodeState);
                    }
                }
            }
        }
        if (sb.length() > 0) { // Remove first space if not empty
            sb.deleteCharAt(0);
        }
        return sb.toString();
    }
}
