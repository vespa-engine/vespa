// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi;

import java.util.LinkedHashMap;
import java.util.Map;

public class DummyBackend {
    public static class Cluster {
        public String id;
        public Map<String, Node> nodes = new LinkedHashMap<>();

        public Cluster(String id) { this.id = id; }
        public Cluster addNode(Node n) { nodes.put(n.id, n); n.clusterId = id; return this; }
    }
    public static class Node {
        public String clusterId;
        public String id;
        public int docCount = 0;
        public String state = "up";
        public String reason = "";
        public String group = "mygroup";

        public Node(String id) { this.id = id; }

        public Node setDocCount(int count) { docCount = count; return this; }
        public Node setState(String state) { this.state = state; return this; }
    }
    private Map<String, Cluster> clusters = new LinkedHashMap<>();

    public Map<String, Cluster> getClusters() { return clusters; }

    public DummyBackend addCluster(Cluster c) {
        clusters.put(c.id, c);
        return this;
    }
}
