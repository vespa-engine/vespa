package com.yahoo.search.dispatch;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.cluster.NodeManager;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.vespa.config.search.DispatchConfig;

// Only needed until query requests are moved to rpc
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.fastsearch.FastSearcher;
import com.yahoo.yolean.Exceptions;
import com.yahoo.prelude.Pong;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A model of a search cluster we might want to dispatch queries to.
 * 
 * @author bratseth
 */
@Beta
public class SearchCluster implements NodeManager<SearchCluster.Node> {

    private static final Logger log = Logger.getLogger(SearchCluster.class.getName());

    /** The min active docs a group must have to be considered up, as a % of the average active docs of the other groups */
    private double minActivedocsCoveragePercentage;
    private final int size;
    private final ImmutableMap<Integer, Group> groups;
    private final ImmutableMultimap<String, Node> nodesByHost;
    private final ClusterMonitor<Node> clusterMonitor;

    // Only needed until query requests are moved to rpc
    private final FS4ResourcePool fs4ResourcePool;

    public SearchCluster(DispatchConfig dispatchConfig, FS4ResourcePool fs4ResourcePool) {
        this(dispatchConfig.min_activedocs_coverage(), toNodes(dispatchConfig), fs4ResourcePool);
    }
    
    public SearchCluster(double minActivedocsCoverage, List<Node> nodes, FS4ResourcePool fs4ResourcePool) {
        this.minActivedocsCoveragePercentage = minActivedocsCoverage;
        size = nodes.size();
        this.fs4ResourcePool = fs4ResourcePool;
        
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
        
        // Set up monitoring of the fs4 interface of the nodes
        // We can switch to monitoring the rpc interface instead when we move the query phase to rpc
        clusterMonitor = new ClusterMonitor<>(this);
        for (Node node : nodes)
            clusterMonitor.add(node, true);
    }
    
    private static ImmutableList<Node> toNodes(DispatchConfig dispatchConfig) {
        ImmutableList.Builder<Node> nodesBuilder = new ImmutableList.Builder<>();
        for (DispatchConfig.Node node : dispatchConfig.node())
            nodesBuilder.add(new Node(node.host(), node.fs4port(), node.group()));
        return nodesBuilder.build();
    }
    
    /** Returns the number of nodes in this cluster (across all groups) */
    public int size() { return size; }
    
    /** Returns the groups of this cluster as an immutable map indexed by group id */
    public ImmutableMap<Integer, Group> groups() { return groups; }

    /** 
     * Returns the nodes of this cluster as an immutable map indexed by host.
     * One host may contain multiple nodes (on different ports), so this is a multi-map.
     */
    public ImmutableMultimap<String, Node> nodesByHost() { return nodesByHost; }

    /** Used by the cluster monitor to manage node status */
    @Override
    public void working(Node node) { node.setWorking(true); }

    /** Used by the cluster monitor to manage node status */
    @Override
    public void failed(Node node) { node.setWorking(false); }

    /** Used by the cluster monitor to manage node status */
    @Override
    public void ping(Node node, Executor executor) {
        Pinger pinger = new Pinger(node);
        FutureTask<Pong> futurePong = new FutureTask<>(pinger);
        executor.execute(futurePong);
        Pong pong = getPong(futurePong, node);
        futurePong.cancel(true);

        if (pong.badResponse())
            clusterMonitor.failed(node, pong.getError(0));
        else
            clusterMonitor.responded(node);
    }

    /**
     * Update statistics after a round of issuing pings.
     * Note that this doesn't wait for pings to return, so it will typically accumulate data from
     * last rounds pinging, or potentially (although unlikely) some combination of new and old data.
     */
    @Override
    public void pingIterationCompleted() {
        // Update active documents per group and use it to decide if the group should be active
        for (Group group : groups.values())
            group.aggregateActiveDocuments();
        if (groups.size() == 1) {
            groups.values().iterator().next().setHasSufficientCoverage(true); // by definition
        } else {
            for (Group currentGroup : groups.values()) {
                long sumOfAactiveDocumentsInOtherGroups = 0;
                for (Group otherGroup : groups.values())
                    if (otherGroup != currentGroup)
                        sumOfAactiveDocumentsInOtherGroups += otherGroup.getActiveDocuments();
                long averageDocumentsInOtherGroups = sumOfAactiveDocumentsInOtherGroups / (groups.size() - 1);
                if (averageDocumentsInOtherGroups == 0)
                    currentGroup.setHasSufficientCoverage(true); // no information about any group; assume coverage
                else
                    currentGroup.setHasSufficientCoverage(
                            100 * (double) currentGroup.getActiveDocuments() / averageDocumentsInOtherGroups > minActivedocsCoveragePercentage);
            }
        }
    }
    
    private Pong getPong(FutureTask<Pong> futurePong, Node node) {
        try {
            return futurePong.get(clusterMonitor.getConfiguration().getFailLimit(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.log(Level.WARNING, "Exception pinging " + node, e);
            return new Pong(ErrorMessage.createUnspecifiedError("Ping was interrupted: " + node));
        } catch (ExecutionException e) {
            log.log(Level.WARNING, "Exception pinging " + node, e);
            return new Pong(ErrorMessage.createUnspecifiedError("Execution was interrupted: " + node));
        } catch (TimeoutException e) {
            return new Pong(ErrorMessage.createNoAnswerWhenPingingNode("Ping thread timed out"));
        }
    }

    private class Pinger implements Callable<Pong> {

        private final Node node;

        public Pinger(Node node) {
            this.node = node;
        }

        public Pong call() {
            try {
                Pong pong = FastSearcher.ping(new Ping(clusterMonitor.getConfiguration().getRequestTimeout()),
                                              fs4ResourcePool.getBackend(node.hostname(), node.fs4port()), node.toString());
                if (pong.activeDocuments().isPresent())
                    node.setActiveDocuments(pong.activeDocuments().get());
                return pong;
            } catch (RuntimeException e) {
                return new Pong(ErrorMessage.createBackendCommunicationError("Exception when pinging " + node + ": "
                                + Exceptions.toMessageString(e)));
            }
        }

    }

    /** A group in a search cluster. This class is multithread safe. */
    public static class Group {
        
        private final int id;
        private final ImmutableList<Node> nodes;
        
        private final AtomicBoolean hasSufficientCoverage = new AtomicBoolean(true);
        private final AtomicLong activeDocuments = new AtomicLong(0);
        
        public Group(int id, List<Node> nodes) {
            this.id = id;
            this.nodes = ImmutableList.copyOf(nodes);
        }

        /** Returns the id of this group */
        public int id() { return id; }
        
        /** Returns the nodes in this group as an immutable list */
        public ImmutableList<Node> nodes() { return nodes; }

        /** 
         * Returns whether this group has sufficient active documents 
         * (compared to other groups) that is should receive traffic 
         */
        public boolean hasSufficientCoverage() {
            return hasSufficientCoverage.get();
        }
        
        void setHasSufficientCoverage(boolean sufficientCoverage) {
            hasSufficientCoverage.lazySet(sufficientCoverage);
        }
        
        
        void aggregateActiveDocuments() {
            long activeDocumentsInGroup = 0;
            for (Node node : nodes)
                activeDocumentsInGroup += node.getActiveDocuments();
            activeDocuments.set(activeDocumentsInGroup);
            
        }

        /** Returns the active documents on this node. If unknown, 0 is returned. */
        long getActiveDocuments() {
            return this.activeDocuments.get();
        }

        @Override
        public String toString() { return "search group " + id; }
        
    }
    
    /** A node in a search cluster. This class is multithread safe. */
    public static class Node {
        
        private final String hostname;
        private final int fs4port;
        private final int group;
        
        private final AtomicBoolean working = new AtomicBoolean(true);
        private final AtomicLong activeDocuments = new AtomicLong(0);

        public Node(String hostname, int fs4port, int group) {
            this.hostname = hostname;
            this.fs4port = fs4port;
            this.group = group;
        }
        
        public String hostname() { return hostname; }

        public int fs4port() { return fs4port; }

        /** Returns the id of this group this node belongs to */
        public int group() { return group; }
        
        void setWorking(boolean working) {
            this.working.lazySet(working);
        }
        
        /** Returns whether this node is currently responding to requests */
        public boolean isWorking() { return working.get(); }
        
        /** Updates the active documents on this node */
        void setActiveDocuments(long activeDocuments) {
            this.activeDocuments.set(activeDocuments);
        }

        /** Returns the active documents on this node. If unknown, 0 is returned. */
        public long getActiveDocuments() {
            return this.activeDocuments.get();
        }

        @Override
        public int hashCode() { return Objects.hash(hostname, fs4port); }
        
        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( ! (o instanceof Node)) return false;
            Node other = (Node)o;
            if ( ! Objects.equals(this.hostname, other.hostname)) return false;
            if ( ! Objects.equals(this.fs4port, other.fs4port)) return false;
            return true;
        }
        
        @Override
        public String toString() { return "search node " + hostname + ":" + fs4port + " in group " + group; }
        
    }

}
