// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.component.annotation.Inject;
import com.yahoo.compress.Compressor;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.SearchPath.InvalidSearchPathException;
import com.yahoo.search.dispatch.rpc.RpcConnectionPool;
import com.yahoo.search.dispatch.rpc.RpcInvokerFactory;
import com.yahoo.search.dispatch.rpc.RpcPingFactory;
import com.yahoo.search.dispatch.rpc.RpcResourcePool;
import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.search.dispatch.searchcluster.SearchGroups;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchNodesConfig;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A dispatcher communicates with search nodes to perform queries and fill hits.
 * <p>
 * This class allocates {@link SearchInvoker} and {@link FillInvoker} objects based
 * on query properties and general system status. The caller can then use the provided
 * invocation object to execute the search or fill.
 * <p>
 * This class is multithread safe.
 *
 * @author bratseth
 * @author ollvir
 */
public class Dispatcher extends AbstractComponent {

    public static final String DISPATCH = "dispatch";
    private static final String TOP_K_PROBABILITY = "topKProbability";
    private static final int MAX_GROUP_SELECTION_ATTEMPTS = 3;

    /** If set will control computation of how many hits will be fetched from each partition.*/
    public static final CompoundName topKProbability = CompoundName.from(DISPATCH + "." + TOP_K_PROBABILITY);

    private final InvokerFactoryFactory invokerFactories;
    private final DispatchConfig dispatchConfig;
    private final RpcConnectionPool rpcResourcePool;
    private final SearchCluster searchCluster;
    private final ClusterMonitor<Node> clusterMonitor;
    private volatile VolatileItems volatileItems;

    private static class VolatileItems {

        final LoadBalancer loadBalancer;
        final InvokerFactory invokerFactory;
        final AtomicInteger inflight = new AtomicInteger(1); // Initial reference.
        Runnable cleanup = () -> { };

        VolatileItems(LoadBalancer loadBalancer, InvokerFactory invokerFactory) {
            this.loadBalancer = loadBalancer;
            this.invokerFactory = invokerFactory;
        }

        private void countDown() {
            if (inflight.decrementAndGet() == 0) cleanup.run();
        }

        private class Ref implements AutoCloseable {
            boolean handedOff = false;
            { inflight.incrementAndGet(); }
            VolatileItems get() { return VolatileItems.this; }
            /** Hands off the reference to the given invoker, which will decrement the counter when closed. */
            <T extends CloseableInvoker> T register(T invoker) {
                invoker.teardown((__, ___) -> countDown());
                handedOff = true;
                return invoker;
            }
            @Override public void close() { if ( ! handedOff) countDown(); }
        }

    }

    private static final QueryProfileType argumentType;

    static {
        argumentType = new QueryProfileType(DISPATCH);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(TOP_K_PROBABILITY, FieldType.doubleType));
        argumentType.freeze();
    }

    public static QueryProfileType getArgumentType() { return argumentType; }

    interface InvokerFactoryFactory {
        InvokerFactory create(RpcConnectionPool rpcConnectionPool, SearchGroups searchGroups, DispatchConfig dispatchConfig);
    }

    @Inject
    public Dispatcher(ComponentId clusterId, DispatchConfig dispatchConfig, DispatchNodesConfig nodesConfig, VipStatus vipStatus) {
        this(clusterId, dispatchConfig, new RpcResourcePool(dispatchConfig, nodesConfig), nodesConfig, vipStatus, RpcInvokerFactory::new);
        initialWarmup(dispatchConfig.warmuptime());
    }

    Dispatcher(ComponentId clusterId, DispatchConfig dispatchConfig, RpcConnectionPool rpcConnectionPool,
               DispatchNodesConfig nodesConfig, VipStatus vipStatus, InvokerFactoryFactory invokerFactories) {
        this(dispatchConfig, rpcConnectionPool,
             new SearchCluster(clusterId.stringValue(), dispatchConfig.minActivedocsPercentage(),
                               toNodes(clusterId.stringValue(), nodesConfig), vipStatus, new RpcPingFactory(rpcConnectionPool)),
             invokerFactories);
    }

    Dispatcher(DispatchConfig dispatchConfig, RpcConnectionPool rpcConnectionPool,
               SearchCluster searchCluster, InvokerFactoryFactory invokerFactories) {
        this(dispatchConfig, rpcConnectionPool, searchCluster, new ClusterMonitor<>(searchCluster, false), invokerFactories);
        this.clusterMonitor.start(); // Populate nodes to monitor before starting it.
    }

    Dispatcher(DispatchConfig dispatchConfig, RpcConnectionPool rpcConnectionPool,
               SearchCluster searchCluster, ClusterMonitor<Node> clusterMonitor, InvokerFactoryFactory invokerFactories) {
        this.dispatchConfig = dispatchConfig;
        this.rpcResourcePool = rpcConnectionPool;
        this.searchCluster = searchCluster;
        this.invokerFactories = invokerFactories;
        this.clusterMonitor = clusterMonitor;
        this.volatileItems = update();
        searchCluster.addMonitoring(clusterMonitor);
    }

    /* For simple mocking in tests. Beware that searchCluster is shutdown in deconstruct() */
    Dispatcher(ClusterMonitor<Node> clusterMonitor, SearchCluster searchCluster,
               DispatchConfig dispatchConfig, InvokerFactory invokerFactory) {
        this(dispatchConfig, null, searchCluster, clusterMonitor, (__, ___, ____) -> invokerFactory);
    }

    /** Returns the snapshot of volatile items that need to be kept together, incrementing its reference counter. */
    private VolatileItems.Ref volatileItems() {
        return volatileItems.new Ref();
    }

    /**
     * This is called whenever we have new config for backend nodes.
     * Normally, we'd want to handle partial failure of the component graph, by reinstating the old state;
     * however, in this case, such a failure would be local to this container, and we instead want to keep
     * the newest config, as that is what most accurately represents the actual backend.
     *
     * The flow of reconfiguration is:
     * 1. The volatile snapshot of disposable items is replaced with a new one that only references updated nodes.
     * 2. Dependencies of the items in 1., which must be configured, are updated, yielding a list of resources to close.
     * 3. When inflight operations against the old snapshot are done, all obsolete resources are cleaned up.
     *
     * Ownership details:
     * 1. The RPC resource pool is owned by the dispatcher, and is updated on node set changes;
     *    it contains the means by which the container talks to backend nodes, so cleanup must be delayed until safe.
     * 2. The invocation factory is owned by the volatile snapshot, and is swapped atomically with it;
     *    it is used by the dispatcher to create ephemeral invokers, which must complete before cleanup (above) can happen.
     * 3. The load balancer is owned by the volatile snapshot, and is swapped atomically with it;
     *    it is used internally by the dispatcher to select search nodes for queries, and is discarded with its snapshot.
     * 4. The cluster monitor is a subordinate to the search cluster, and does whatever that tells it to, at any time;
     *    it is technically owned by the dispatcher, but in updated by the search cluster, when that is updated.
     * 5. The search cluster is owned by the dispatcher, and is updated on node set changes;
     *    its responsibility is to keep track of the state of the backend, and to provide a view of it to the dispatcher,
     *    as well as keep the container vip status updated accordingly; it should therefore preserve as much as possible
     *    of its state across reconfigurations: with new node config, it will immediately forget obsolete nodes, and set
     *    coverage information as if the new nodes have zero documents, before even checking their status; this is fine
     *    under the assumption that this is the common case, i.e., new nodes have no documents yet.
     */
    void updateWithNewConfig(DispatchNodesConfig nodesConfig) {
        try (var items = volatileItems()) { // Mark a reference to the old snapshot, which we want to have cleaned up.
            items.get().countDown();        // Decrement for its initial creation reference, so it may reach 0.

            // Let the RPC pool know about the new nodes, and set up the delayed cleanup that we need to do.
            Collection<? extends AutoCloseable> connectionPoolsToClose = rpcResourcePool.updateNodes(nodesConfig);
            items.get().cleanup = () -> {
                for (AutoCloseable pool : connectionPoolsToClose) {
                    try { pool.close(); } catch (Exception ignored) { }
                }
            };

            // Update the nodes the search cluster keeps track of, and what nodes are monitored.
            searchCluster.updateNodes(toNodes(searchCluster.name(), nodesConfig), clusterMonitor, dispatchConfig.minActivedocsPercentage());

            // Update the snapshot to use the new nodes set in the search cluster; the RPC pool is ready for this.
            this.volatileItems = update();
        }   // Close the old snapshot, which may trigger the RPC cleanup now, or when the last invoker is closed, by a search thread.
    }

    private VolatileItems update() {
        return new VolatileItems(new LoadBalancer(searchCluster.groupList().groups(), toLoadBalancerPolicy(dispatchConfig.distributionPolicy())),
                                 invokerFactories.create(rpcResourcePool, searchCluster.groupList(), dispatchConfig));
    }

    private void initialWarmup(double warmupTime) {
        Thread warmup = new Thread(() -> warmup(warmupTime));
        warmup.start();
        try {
            while ( ! searchCluster.hasInformationAboutAllNodes()) {
                Thread.sleep(1);
            }
            warmup.join();
        } catch (InterruptedException e) {}

        // Now we have information from all nodes and a ping iteration has completed.
        // Instead of waiting until next ping interval to update coverage and group state,
        // we should compute the state ourselves, so that when the dispatcher is ready the state
        // of its groups are also known.
        searchCluster.pingIterationCompleted();
    }

    private static LoadBalancer.Policy toLoadBalancerPolicy(DispatchConfig.DistributionPolicy.Enum policy) {
        return switch (policy) {
            case ROUNDROBIN -> LoadBalancer.Policy.ROUNDROBIN;
            case BEST_OF_RANDOM_2 -> LoadBalancer.Policy.BEST_OF_RANDOM_2;
            case ADAPTIVE,LATENCY_AMORTIZED_OVER_REQUESTS -> LoadBalancer.Policy.LATENCY_AMORTIZED_OVER_REQUESTS;
            case LATENCY_AMORTIZED_OVER_TIME -> LoadBalancer.Policy.LATENCY_AMORTIZED_OVER_TIME;
        };
    }
    private static List<Node> toNodes(String clusterName, DispatchNodesConfig nodesConfig) {
        return nodesConfig.node().stream()
                .map(n -> new Node(clusterName, n.key(), n.host(), n.group()))
                .toList();
    }

    /**
     * Will run important code in order to trigger JIT compilation and avoid cold start issues.
     * Currently warms up lz4 compression code.
     */
    private static void warmup(double seconds) {
        new Compressor().warmup(seconds);
    }

    public boolean allGroupsHaveSize1() {
        return searchCluster.groupList().groups().stream().allMatch(g -> g.nodes().size() == 1);
    }

    @Override
    public void deconstruct() {
        // The clustermonitor must be shutdown first as it uses the invokerfactory through the searchCluster.
        clusterMonitor.shutdown();
        if (rpcResourcePool != null) {
            rpcResourcePool.close();
        }
    }

    public FillInvoker getFillInvoker(Result result, VespaBackEndSearcher searcher) {
        try (var items = volatileItems()) { // Take a snapshot, and release it when we're done.
            return items.register(items.get().invokerFactory.createFillInvoker(searcher, result));
        }
    }

    public SearchInvoker getSearchInvoker(Query query, VespaBackEndSearcher searcher) {
        try (var items = volatileItems()) { // Take a snapshot, and release it when we're done.
            int maxHitsPerNode = dispatchConfig.maxHitsPerNode();
            SearchInvoker invoker = getSearchPathInvoker(query, searcher, searchCluster.groupList(), items.get().invokerFactory, maxHitsPerNode)
                    .orElseGet(() -> getInternalInvoker(query, searcher, searchCluster, items.get().loadBalancer, items.get().invokerFactory, maxHitsPerNode));

            if (query.properties().getBoolean(com.yahoo.search.query.Model.ESTIMATE)) {
                query.setHits(0);
                query.setOffset(0);
            }
            return items.register(invoker);
        }
    }

    /** Builds an invoker based on searchpath */
    private static Optional<SearchInvoker> getSearchPathInvoker(Query query, VespaBackEndSearcher searcher, SearchGroups cluster,
                                                                InvokerFactory invokerFactory, int maxHitsPerNode) {
        String searchPath = query.getModel().getSearchPath();
        if (searchPath == null) return Optional.empty();

        try {
            List<Node> nodes = SearchPath.selectNodes(searchPath, cluster);
            if (nodes.isEmpty()) return Optional.empty();

            query.trace(false, 2, "Dispatching with search path ", searchPath);
            return invokerFactory.createSearchInvoker(searcher,
                                                      query,
                                                      nodes,
                                                      true,
                                                      maxHitsPerNode);
        } catch (InvalidSearchPathException e) {
            return Optional.of(new SearchErrorInvoker(ErrorMessage.createIllegalQuery(e.getMessage())));
        }
    }

    private static SearchInvoker getInternalInvoker(Query query, VespaBackEndSearcher searcher, SearchCluster cluster,
                                                    LoadBalancer loadBalancer, InvokerFactory invokerFactory, int maxHitsPerNode) {
        Optional<Node> directNode = cluster.localCorpusDispatchTarget();
        if (directNode.isPresent()) {
            Node node = directNode.get();
            query.trace(false, 2, "Dispatching to ", node);
            return invokerFactory.createSearchInvoker(searcher,
                                                      query,
                                                      List.of(node),
                                                      true,
                                                      maxHitsPerNode)
                                 .orElseThrow(() -> new IllegalStateException("Could not dispatch directly to " + node));
        }

        int covered = cluster.groupsWithSufficientCoverage();
        int groups = cluster.groupList().size();
        int max = Integer.min(Integer.min(covered + 1, groups), MAX_GROUP_SELECTION_ATTEMPTS);
        Set<Integer> rejected = rejectGroupBlockingFeed(cluster.groupList().groups());
        for (int i = 0; i < max; i++) {
            Optional<Group> groupInCluster = loadBalancer.takeGroup(rejected);
            if (groupInCluster.isEmpty()) break; // No groups available

            Group group = groupInCluster.get();
            boolean acceptIncompleteCoverage = (i == max - 1);
            Optional<SearchInvoker> invoker = invokerFactory.createSearchInvoker(searcher,
                                                                                 query,
                                                                                 group.nodes(),
                                                                                 acceptIncompleteCoverage,
                                                                                 maxHitsPerNode);
            if (invoker.isPresent()) {
                query.trace(false, 2, "Dispatching to group ", group.id(), " after retries = ", i);
                query.getModel().setSearchPath("/" + group.id());
                invoker.get().teardown((success, time) -> loadBalancer.releaseGroup(group, success, time));
                return invoker.get();
            } else {
                loadBalancer.releaseGroup(group, false, RequestDuration.of(Duration.ZERO));
                if (rejected == null) {
                    rejected = new HashSet<>();
                }
                rejected.add(group.id());
            }
        }
        throw new IllegalStateException("No suitable groups to dispatch query. Rejected: " + rejected);
    }

    /**
     * We want to avoid groups blocking feed because their data may be out of date.
     * If there is a single group blocking feed, we want to reject it.
     * If multiple groups are blocking feed we should use them anyway as we may not have remaining
     * capacity otherwise. Same if there are no other groups.
     *
     * @return a modifiable set containing the single group to reject, or null otherwise
     */
    private static Set<Integer> rejectGroupBlockingFeed(Collection<Group> groups) {
        if (groups.size() == 1) return null;
        List<Group> groupsRejectingFeed = groups.stream().filter(Group::isBlockingWrites).toList();
        if (groupsRejectingFeed.size() != 1) return null;
        Set<Integer> rejected = new HashSet<>();
        rejected.add(groupsRejectingFeed.get(0).id());
        return rejected;
    }

}
