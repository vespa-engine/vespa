// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.compress.Compressor;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.SearchPath.InvalidSearchPathException;
import com.yahoo.search.dispatch.rpc.RpcInvokerFactory;
import com.yahoo.search.dispatch.rpc.RpcPingFactory;
import com.yahoo.search.dispatch.rpc.RpcResourcePool;
import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.SearchGroups;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
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

/**
 * A dispatcher communicates with search nodes to perform queries and fill hits.
 *
 * This class allocates {@link SearchInvoker} and {@link FillInvoker} objects based
 * on query properties and general system status. The caller can then use the provided
 * invocation object to execute the search or fill.
 *
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

    private final DispatchConfig dispatchConfig;
    private final RpcResourcePool rpcResourcePool;
    private final SearchCluster searchCluster;
    private final ClusterMonitor<Node> clusterMonitor;
    private volatile VolatileItems volatileItems;

    private static class VolatileItems {
        final LoadBalancer loadBalancer;
        final InvokerFactory invokerFactory;
        VolatileItems(LoadBalancer loadBalancer, InvokerFactory invokerFactory) {
            this.loadBalancer = loadBalancer;
            this.invokerFactory = invokerFactory;
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

    @Inject
    public Dispatcher(ComponentId clusterId, DispatchConfig dispatchConfig,
                      DispatchNodesConfig nodesConfig, VipStatus vipStatus) {
        this.dispatchConfig = dispatchConfig;
        rpcResourcePool = new RpcResourcePool(dispatchConfig, nodesConfig);
        searchCluster = new SearchCluster(clusterId.stringValue(), dispatchConfig.minActivedocsPercentage(),
                                          toNodes(nodesConfig), vipStatus, new RpcPingFactory(rpcResourcePool));
        clusterMonitor = new ClusterMonitor<>(searchCluster, true);
        volatileItems = update(null);
        initialWarmup(dispatchConfig.warmuptime());
    }

    /* For simple mocking in tests. Beware that searchCluster is shutdown on in deconstruct() */
    Dispatcher(ClusterMonitor<Node> clusterMonitor, SearchCluster searchCluster,
               DispatchConfig dispatchConfig, InvokerFactory invokerFactory) {
        this.dispatchConfig = dispatchConfig;
        this.rpcResourcePool = null;
        this.searchCluster = searchCluster;
        this.clusterMonitor = clusterMonitor;
        this.volatileItems = update(invokerFactory);
    }

    private VolatileItems update(InvokerFactory invokerFactory) {
        var items = new VolatileItems(new LoadBalancer(searchCluster.groupList().groups(), toLoadBalancerPolicy(dispatchConfig.distributionPolicy())),
                                      (invokerFactory == null)
                                             ? new RpcInvokerFactory(rpcResourcePool, searchCluster.groupList(), dispatchConfig)
                                             : invokerFactory);
        searchCluster.addMonitoring(clusterMonitor);
        return items;
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
            case ROUNDROBIN: yield LoadBalancer.Policy.ROUNDROBIN;
            case BEST_OF_RANDOM_2: yield LoadBalancer.Policy.BEST_OF_RANDOM_2;
            case ADAPTIVE,LATENCY_AMORTIZED_OVER_REQUESTS: yield LoadBalancer.Policy.LATENCY_AMORTIZED_OVER_REQUESTS;
            case LATENCY_AMORTIZED_OVER_TIME: yield LoadBalancer.Policy.LATENCY_AMORTIZED_OVER_TIME;
        };
    }
    private static List<Node> toNodes(DispatchNodesConfig nodesConfig) {
        return nodesConfig.node().stream()
                .map(n -> new Node(n.key(), n.host(), n.group()))
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
        return volatileItems.invokerFactory.createFillInvoker(searcher, result);
    }

    public SearchInvoker getSearchInvoker(Query query, VespaBackEndSearcher searcher) {
        VolatileItems items = volatileItems; // Take a snapshot
        int maxHitsPerNode = dispatchConfig.maxHitsPerNode();
        SearchInvoker invoker = getSearchPathInvoker(query, searcher, searchCluster.groupList(), items.invokerFactory, maxHitsPerNode)
                .orElseGet(() -> getInternalInvoker(query, searcher, searchCluster, items.loadBalancer, items.invokerFactory, maxHitsPerNode));

        if (query.properties().getBoolean(com.yahoo.search.query.Model.ESTIMATE)) {
            query.setHits(0);
            query.setOffset(0);
        }
        return invoker;
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
