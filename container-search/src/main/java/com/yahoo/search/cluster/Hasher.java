// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.cluster;

/**
 * A hasher load balances between a set of nodes, represented by object ids.
 *
 * @author Arne B Fossaa
 * @author bratseth
 * @author Prashanth B. Bhat
 */
public class Hasher<T> {

    public static class NodeFactor<T> {

        private final T node;

        /**
         * The relative weight of the different nodes.
         * Hashing are based on the proportions of the weights.
         */
        private final int load;

        public NodeFactor(T node, int load) {
            this.node = node;
            this.load = load;
        }

        public final T getNode() { return node; }

        public final int getLoad() { return load; }

    }

    public static class NodeList<T> {

        private final NodeFactor<T>[] nodes;

        private int totalLoadFactor;

        public NodeList(NodeFactor<T>[] nodes) {
            this.nodes = nodes;
            totalLoadFactor = 0;
            if(nodes != null) {
                for(NodeFactor<T> node:nodes) {
                    totalLoadFactor += node.getLoad();
                }
            }
        }

        public int getNodeCount() {
            return nodes.length;
        }

        public T select(int code, int trynum) {
            if (totalLoadFactor <= 0) return null;

            // Multiply by a prime number much bigger than the likely number of hosts
            int hashValue=(Math.abs(code*76103)) % totalLoadFactor;
            int sumLoad=0;
            int targetNode;
            for (targetNode=0; targetNode<nodes.length; targetNode++) {
                sumLoad +=nodes[targetNode].getLoad();
                if (sumLoad > hashValue)
                    break;
            }
            
            // Skip the ones we have tried before.
            targetNode += trynum;
            targetNode %= nodes.length;
            return nodes[targetNode].getNode();
        }

        public boolean hasNode(T node) {
            for (int i = 0;i<nodes.length;i++) {
                if(node == nodes[i].getNode()) {
                    return true;
                }
            }
            return false;
        }

    }

    private volatile NodeList<T> nodes;

    @SuppressWarnings("unchecked")
    public Hasher() {
        this.nodes = new NodeList<T>(new NodeFactor[0]);
    }

    /** Adds a node with load factor 100 */
    public void add(T node) {
        add(node,100);
    }

    /**
     * Adds a code with a load factor.
     * The load factor is relative to the load of the other added nodes
     * and determines how often this node will be selected compared
     * to the other nodes
     */
    public synchronized void add(T node, int load) {
        if ( ! nodes.hasNode(node)) {
            NodeFactor<T>[] oldNodes = nodes.nodes;
            @SuppressWarnings("unchecked")
            NodeFactor<T>[] newNodes = (NodeFactor<T>[]) new NodeFactor[oldNodes.length + 1];
            System.arraycopy(oldNodes, 0, newNodes, 0, oldNodes.length);
            newNodes[newNodes.length - 1] = new NodeFactor<>(node, load);

            //Atomic switch due to volatile
            nodes = new NodeList<>(newNodes);
        }
    }

    /** Removes a node */
    public synchronized void remove(T node) {
        if (nodes.hasNode(node)) {
            NodeFactor<T>[] oldNodes = nodes.nodes;
            @SuppressWarnings("unchecked")
            NodeFactor<T>[] newNodes = (NodeFactor<T>[]) new NodeFactor[oldNodes.length - 1];
            for (int i = 0, j = 0; i < oldNodes.length; i++) {
                if (oldNodes[i].getNode() != node) {
                    newNodes[j++] = oldNodes[i];
                }
            }
            // An atomic switch due to volatile.
            nodes = new NodeList<>(newNodes);
        }
    }

    /** Returns a list of nodes that are up.*/
    public NodeList<T> getNodes() {
        return nodes;
    }

}
