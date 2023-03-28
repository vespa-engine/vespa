// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.state;
import com.yahoo.text.StringUtilities;

import java.text.ParseException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Be careful about changing this class, as it mirrors the ClusterState in C++.
 * Please update both if you need to change anything.
 */
public class ClusterState implements Cloneable {

    private static final NodeState DEFAULT_STORAGE_UP_NODE_STATE = new NodeState(NodeType.STORAGE, State.UP);
    private static final NodeState DEFAULT_DISTRIBUTOR_UP_NODE_STATE = new NodeState(NodeType.DISTRIBUTOR, State.UP);
    private static final NodeState DEFAULT_STORAGE_DOWN_NODE_STATE = new NodeState(NodeType.STORAGE, State.DOWN);
    private static final NodeState DEFAULT_DISTRIBUTOR_DOWN_NODE_STATE = new NodeState(NodeType.DISTRIBUTOR, State.DOWN);

    /**
     * Maintains a bitset where all non-down nodes have a bit set. All nodes that differ from defaultUp
     * and defaultDown are store explicit in a hash map.
     */
    private static class Nodes {
        private int logicalNodeCount;
        private final NodeType type;
        private final BitSet upNodes;
        private final Map<Integer, NodeState> nodeStates = new HashMap<>();
        Nodes(NodeType type) {
            this.type = type;
            upNodes = new BitSet();
        }
        Nodes(Nodes b) {
            logicalNodeCount = b.logicalNodeCount;
            type = b.type;
            upNodes = (BitSet) b.upNodes.clone();
            b.nodeStates.forEach((key, value) -> nodeStates.put(key, value.clone()));
        }

        void updateMaxIndex(int index) {
            if (index > logicalNodeCount) {
                upNodes.set(logicalNodeCount, index);
                logicalNodeCount = index;
            }
        }

        int getLogicalNodeCount() { return logicalNodeCount; }

        NodeState getNodeState(int index) {
            NodeState ns = nodeStates.get(index);
            if (ns != null) return ns;
            return (index >= getLogicalNodeCount() || ! upNodes.get(index))
                    ? new NodeState(type, State.DOWN)
                    : new NodeState(type, State.UP);
        }

        private void validateInput(Node node, NodeState ns) {
            ns.verifyValidInSystemState(node.getType());
            if (node.getType() != type) {
                throw new IllegalArgumentException("NodeType '" + node.getType() + "' differs from '" + type + "'");
            }
        }

        void setNodeState(Node node, NodeState ns) {
            validateInput(node, ns);
            int index = node.getIndex();
            if (index >= logicalNodeCount) {
                logicalNodeCount = index + 1;
            }
            setNodeStateInternal(index, ns);
        }

        void addNodeState(Node node, NodeState ns) {
            validateInput(node, ns);
            int index = node.getIndex();
            updateMaxIndex(index + 1);
            setNodeStateInternal(index, ns);
        }

        private static boolean equalsWithDescription(NodeState a, NodeState b) {
            // This is due to NodeState.equals considers semantic equality, and description is not part of that.
            return a.equals(b) && ((a.getState() != State.DOWN) || a.getDescription().equals(b.getDescription()));
        }

        private void setNodeStateInternal(int index, NodeState ns) {
            nodeStates.remove(index);
            if (ns.getState() == State.DOWN) {
                upNodes.clear(index);
                if ( ! equalsWithDescription(defaultDown(), ns)) {
                    nodeStates.put(index, ns);
                }
            } else {
                upNodes.set(index);
                if ( ! equalsWithDescription(defaultUp(), ns)) {
                    nodeStates.put(index, ns);
                }
            }
        }

        boolean notSimilarTo(Nodes other, final NodeStateCmp nodeStateCmp) {
            // TODO verify behavior of C++ impl against this
            if (logicalNodeCount != other.logicalNodeCount) return true;
            if (type != other.type) return true;
            if ( ! upNodes.equals(other.upNodes)) return true;
            for (Integer node : unionNodeSetWith(other.nodeStates.keySet())) {
                final NodeState lhs = nodeStates.get(node);
                final NodeState rhs = other.nodeStates.get(node);
                if (!nodeStateCmp.similar(type, lhs, rhs)) {
                    return true;
                }
            }
            return false;
        }

        private Set<Integer> unionNodeSetWith(final Set<Integer> otherNodes) {
            final Set<Integer> unionNodeSet = new HashSet<>(nodeStates.keySet());
            unionNodeSet.addAll(otherNodes);
            return unionNodeSet;
        }

        @Override
        public String toString() { return toString(false); }

        String toString(boolean verbose) {
            StringBuilder sb = new StringBuilder();

            int nodeCount = verbose ? getLogicalNodeCount() : upNodes.length();
            if ( nodeCount > 0 ) {
                sb.append(type == NodeType.DISTRIBUTOR ? " distributor:" : " storage:").append(nodeCount);
                for (int i = 0; i < nodeCount; i++) {
                    String nodeState = getNodeState(i).serialize(i, verbose);
                    if (!nodeState.isEmpty()) {
                        sb.append(' ').append(nodeState);
                    }
                }
            }
            return sb.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (! (obj instanceof Nodes b)) return false;
            if (logicalNodeCount != b.logicalNodeCount) return false;
            if (type != b.type) return false;
            if (!upNodes.equals(b.upNodes)) return false;
            if (!nodeStates.equals(b.nodeStates)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(logicalNodeCount, type, nodeStates, upNodes);
        }
        private NodeState defaultDown() {
            return type == NodeType.STORAGE
                    ? DEFAULT_STORAGE_DOWN_NODE_STATE
                    : DEFAULT_DISTRIBUTOR_DOWN_NODE_STATE;
        }
        private NodeState defaultUp() {
            return defaultUpNodeState(type);
        }
    }

    private int version = 0;
    private State state = State.DOWN;
    private String description = "";
    private int distributionBits = 16;

    private final Nodes distributorNodes;
    private final Nodes storageNodes;

    public ClusterState(String serialized) throws ParseException {
        distributorNodes = new Nodes(NodeType.DISTRIBUTOR);
        storageNodes = new Nodes(NodeType.STORAGE);
        deserialize(serialized);
    }

    public ClusterState(ClusterState b) {
        version = b.version;
        state = b.state;
        description = b.description;
        distributionBits = b.distributionBits;
        distributorNodes = new Nodes(b.distributorNodes);
        storageNodes = new Nodes(b.storageNodes);
    }

    private Nodes getNodes(NodeType type) {
        return (type == NodeType.STORAGE)
                ? storageNodes
                : (type == NodeType.DISTRIBUTOR) ? distributorNodes : null;
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
        return new ClusterState(this);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ClusterState other)) { return false; }
        if (version != other.version
            || !state.equals(other.state)
            || distributionBits != other.distributionBits
            || !distributorNodes.equals(other.distributorNodes)
            || !storageNodes.equals(other.storageNodes))
        {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(version, state, distributionBits, distributorNodes, storageNodes);
    }

    @FunctionalInterface
    private interface NodeStateCmp {
        boolean similar(NodeType nodeType, NodeState lhs, NodeState rhs);
    }

    public boolean similarTo(Object o) {
        if (!(o instanceof final ClusterState other)) { return false; }

        return similarToImpl(other, this::normalizedNodeStateSimilarTo);
    }

    public boolean similarToIgnoringInitProgress(final ClusterState other) {
        return similarToImpl(other, this::normalizedNodeStateSimilarToIgnoringInitProgress);
    }

    private boolean similarToImpl(ClusterState other, final NodeStateCmp nodeStateCmp) {
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
        if (distributorNodes.notSimilarTo(other.distributorNodes, nodeStateCmp)) return false;
        if (storageNodes.notSimilarTo(other.storageNodes, nodeStateCmp)) return false;
        return true;
    }

    private boolean metaInformationSimilarTo(final ClusterState other) {
        return version == other.version
                && state.equals(other.state)
                && distributionBits == other.distributionBits;
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

    /** Used during deserialization */
    private class NodeData {

        boolean empty = true;
        Node node = new Node(NodeType.STORAGE, 0);
        StringBuilder sb = new StringBuilder();

        void addNodeState() throws ParseException {
            if (!empty) {
                NodeState ns = NodeState.deserialize(node.getType(), sb.toString());
                getNodes(node.getType()).addNodeState(node, ns);
            }
            empty = true;
            sb = new StringBuilder();
        }
    }

    private void deserialize(String serialized) throws ParseException {
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
                    try {
                        setVersion(Integer.parseInt(value));
                    } catch (Exception e) {
                        throw new ParseException("Illegal version '" + value + "'. Must be an integer, in state: " + serialized, 0);
                    }
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
                        nodeCount = Integer.parseInt(value);
                    } catch (Exception e) {
                        throw new ParseException("Illegal node count '" + value + "' in state: " + serialized, 0);
                    }
                    getNodes(nodeType).updateMaxIndex(nodeCount);
                    continue;
                }
                int dot2 = key.indexOf('.', dot + 1);
                Node node;
                if (dot2 < 0) {
                    node = new Node(nodeType, Integer.parseInt(key.substring(dot + 1)));
                } else {
                    node = new Node(nodeType, Integer.parseInt(key.substring(dot + 1, dot2)));
                }
                if (node.getIndex() >= getNodeCount(nodeType)) {
                    throw new ParseException("Cannot index " + nodeType + " node " + node.getIndex() + " of " + getNodeCount(nodeType) + " in state: " + serialized, 0);
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
    }

    public String getTextualDifference(ClusterState other) {
        return getDiff(other).toString();
    }
    public String getHtmlDifference(ClusterState other) {
        return getDiff(other).toHtml();
    }

    private Diff getDiff(ClusterState other) {
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
    public int getNodeCount(NodeType type) { return getNodes(type).getLogicalNodeCount(); }

    /**
     * Returns the state of a node.
     * If the node is not known this returns a node in the state UP (never null) if it has lower index than the max
     * and DOWN otherwise.
     */
    public NodeState getNodeState(Node node) {
        return getNodes(node.getType()).getNodeState(node.getIndex());
    }

    /**
     * Set the node state of the given node.
     *
     * Automatically adjusts number of nodes of that given type if out of range of current nodes seen.
     */
    public void setNodeState(Node node, NodeState newState) {
        newState.verifyValidInSystemState(node.getType());
        getNodes(node.getType()).setNodeState(node, newState);
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

        sb.append(distributorNodes.toString(verbose));
        sb.append(storageNodes.toString(verbose));

        if (sb.length() > 0) { // Remove first space if not empty
            sb.deleteCharAt(0);
        }
        return sb.toString();
    }
}
