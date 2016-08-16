package com.yahoo.search.dispatch;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.yahoo.vespa.config.search.DispatchConfig;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A model of a search cluster we might want to dispatch queries to.
 * 
 * @author bratseth
 */
public class SearchCluster {

    private final ImmutableMap<Integer, Group> groups;
    private final ImmutableMultimap<String, Node> nodesByHost;

    public SearchCluster(DispatchConfig dispatchConfig) {
        this(toNodes(dispatchConfig));
    }
    
    public SearchCluster(List<Node> nodes) {
        // Create groups
        ImmutableMap.Builder<Integer, Group> groupsBuilder = new ImmutableMap.Builder<>();
        for (Map.Entry<Integer, List<Node>> group : nodes.stream().collect(Collectors.groupingBy(Node::group)).entrySet())
            groupsBuilder.put(group.getKey(), new Group(group.getKey(), group.getValue()));
        groups = groupsBuilder.build();
        
        // Index nodes by host
        ImmutableMultimap.Builder<String, Node> nodesByHostBuilder = new ImmutableMultimap.Builder<>();
        for (Node node : nodes)
            nodesByHostBuilder.put(node.hostname(), node);
        nodesByHost = nodesByHostBuilder.build();
    }
    
    private static ImmutableList<Node> toNodes(DispatchConfig dispatchConfig) {
        ImmutableList.Builder<Node> nodesBuilder = new ImmutableList.Builder<>();
        for (DispatchConfig.Node node : dispatchConfig.node())
            nodesBuilder.add(new Node(node.host(), node.port(), node.group()));
        return nodesBuilder.build();
    }
    
    /** Returns the groups of this cluster as an immutable map indexed by group id */
    public ImmutableMap<Integer, Group> groups() { return groups; }

    /** 
     * Returns the nodes of this cluster as an immutable map indexed by host.
     * One host may contain multiple nodes (on different ports), so this is a multi-map.
     */
    public ImmutableMultimap<String, Node> nodesByHost() { return nodesByHost; }
    
    public static class Group {
        
        private final int id;
        private final ImmutableList<Node> nodes;
        
        public Group(int id, List<Node> nodes) {
            this.id = id;
            this.nodes = ImmutableList.copyOf(nodes);
        }

        /** Returns the id of this group */
        public int id() { return id; }
        
        /** Returns the nodes in this group as an immutable list */
        public ImmutableList<Node> nodes() { return nodes; }
        
    }
    
    public static class Node {
        
        private final String hostname;
        private final int port;
        private final int group;
        
        public Node(String hostname, int port, int group) {
            this.hostname = hostname;
            this.port = port;
            this.group = group;
        }
        
        public String hostname() { return hostname; }
        public int port() { return port; }

        /** Returns the id of this group this node belongs to */
        public int group() { return group; }
        
    }

}
