// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.component.AbstractComponent;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.jdisc.Metric;
import com.yahoo.prelude.fastsearch.FS4InvokerFactory;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.SearchPath.InvalidSearchPathException;
import com.yahoo.search.dispatch.rpc.RpcInvokerFactory;
import com.yahoo.search.dispatch.rpc.RpcResourcePool;
import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.vespa.config.search.DispatchConfig;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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
    private static final String FDISPATCH_METRIC = "dispatch_fdispatch";
    private static final String INTERNAL_METRIC = "dispatch_internal";

    private static final int MAX_GROUP_SELECTION_ATTEMPTS = 3;

    /** If enabled, this internal dispatcher will be preferred over fdispatch whenever possible */
    private static final CompoundName dispatchInternal = new CompoundName("dispatch.internal");

    /** If enabled, search queries will use protobuf rpc */
    private static final CompoundName dispatchProtobuf = new CompoundName("dispatch.protobuf");

    /** A model of the search cluster this dispatches to */
    private final SearchCluster searchCluster;

    private final LoadBalancer loadBalancer;
    private final boolean multilevelDispatch;
    private final boolean internalDispatchByDefault;

    private final FS4InvokerFactory fs4InvokerFactory;
    private final RpcInvokerFactory rpcInvokerFactory;

    private final Metric metric;
    private final Metric.Context metricContext;

    public Dispatcher(String clusterId, DispatchConfig dispatchConfig, FS4ResourcePool fs4ResourcePool, int containerClusterSize,
            VipStatus vipStatus, Metric metric) {
        this(new SearchCluster(clusterId, dispatchConfig, fs4ResourcePool, containerClusterSize, vipStatus), dispatchConfig,
                fs4ResourcePool, new RpcResourcePool(dispatchConfig), metric);
    }

    public Dispatcher(SearchCluster searchCluster, DispatchConfig dispatchConfig, FS4ResourcePool fs4ResourcePool,
            RpcResourcePool rpcResourcePool, Metric metric) {
        this(searchCluster, dispatchConfig, new FS4InvokerFactory(fs4ResourcePool, searchCluster),
                new RpcInvokerFactory(rpcResourcePool, searchCluster), metric);
    }

    public Dispatcher(SearchCluster searchCluster, DispatchConfig dispatchConfig, FS4InvokerFactory fs4InvokerFactory,
            RpcInvokerFactory rpcInvokerFactory, Metric metric) {
        this.searchCluster = searchCluster;
        this.loadBalancer = new LoadBalancer(searchCluster,
                dispatchConfig.distributionPolicy() == DispatchConfig.DistributionPolicy.ROUNDROBIN);
        this.multilevelDispatch = dispatchConfig.useMultilevelDispatch();
        this.internalDispatchByDefault = !dispatchConfig.useFdispatchByDefault();

        this.fs4InvokerFactory = fs4InvokerFactory;
        this.rpcInvokerFactory = rpcInvokerFactory;

        this.metric = metric;
        this.metricContext = metric.createContext(null);
    }

    /** Returns the search cluster this dispatches to */
    public SearchCluster searchCluster() {
        return searchCluster;
    }

    @Override
    public void deconstruct() {
        rpcInvokerFactory.release();
    }

    public Optional<FillInvoker> getFillInvoker(Result result, VespaBackEndSearcher searcher) {
        Optional<FillInvoker> rpcInvoker = rpcInvokerFactory.createFillInvoker(searcher, result);
        if (rpcInvoker.isPresent()) {
            return rpcInvoker;
        }
        if (result.getQuery().properties().getBoolean(dispatchInternal, internalDispatchByDefault)) {
            Optional<FillInvoker> fs4Invoker = fs4InvokerFactory.createFillInvoker(searcher, result);
            if (fs4Invoker.isPresent()) {
                return fs4Invoker;
            }
        }
        return Optional.empty();
    }

    public Optional<SearchInvoker> getSearchInvoker(Query query, VespaBackEndSearcher searcher) {
        if (multilevelDispatch || !query.properties().getBoolean(dispatchInternal, internalDispatchByDefault)) {
            emitDispatchMetric(Optional.empty());
            return Optional.empty();
        }

        InvokerFactory factory = query.properties().getBoolean(dispatchProtobuf, false) ? rpcInvokerFactory : fs4InvokerFactory;

        Optional<SearchInvoker> invoker = getSearchPathInvoker(query, factory, searcher);

        if (!invoker.isPresent()) {
            invoker = getInternalInvoker(query, factory, searcher);
        }
        if (invoker.isPresent() && query.properties().getBoolean(com.yahoo.search.query.Model.ESTIMATE)) {
            query.setHits(0);
            query.setOffset(0);
        }
        emitDispatchMetric(invoker);
        return invoker;
    }

    public FS4InvokerFactory getFS4InvokerFactory() {
        return fs4InvokerFactory;
    }

    // build invoker based on searchpath
    private Optional<SearchInvoker> getSearchPathInvoker(Query query, InvokerFactory invokerFactory, VespaBackEndSearcher searcher) {
        String searchPath = query.getModel().getSearchPath();
        if (searchPath == null) {
            return Optional.empty();
        }
        try {
            List<Node> nodes = SearchPath.selectNodes(searchPath, searchCluster);
            if (nodes.isEmpty()) {
                return Optional.empty();
            } else {
                query.trace(false, 2, "Dispatching internally with search path ", searchPath);
                return invokerFactory.createSearchInvoker(searcher, query, OptionalInt.empty(), nodes, true);
            }
        } catch (InvalidSearchPathException e) {
            return Optional.of(new SearchErrorInvoker(ErrorMessage.createIllegalQuery(e.getMessage())));
        }
    }

    private Optional<SearchInvoker> getInternalInvoker(Query query, InvokerFactory invokerFactory, VespaBackEndSearcher searcher) {
        Optional<Node> directNode = searchCluster.directDispatchTarget();
        if (directNode.isPresent()) {
            Node node = directNode.get();
            query.trace(false, 2, "Dispatching directly to ", node);
            return invokerFactory.createSearchInvoker(searcher, query, OptionalInt.empty(), Arrays.asList(node), true);
        }

        int covered = searchCluster.groupsWithSufficientCoverage();
        int groups = searchCluster.orderedGroups().size();
        int max = Integer.min(Integer.min(covered + 1, groups), MAX_GROUP_SELECTION_ATTEMPTS);
        Set<Integer> rejected = null;
        for (int i = 0; i < max; i++) {
            Optional<Group> groupInCluster = loadBalancer.takeGroup(rejected);
            if (!groupInCluster.isPresent()) {
                // No groups available
                break;
            }
            Group group = groupInCluster.get();
            boolean acceptIncompleteCoverage = (i == max - 1);
            Optional<SearchInvoker> invoker = invokerFactory.createSearchInvoker(searcher, query, OptionalInt.of(group.id()), group.nodes(),
                    acceptIncompleteCoverage);
            if (invoker.isPresent()) {
                query.trace(false, 2, "Dispatching internally to search group ", group.id());
                query.getModel().setSearchPath("/" + group.id());
                invoker.get().teardown((success, time) -> loadBalancer.releaseGroup(group, success, time));
                return invoker;
            } else {
                loadBalancer.releaseGroup(group, false, 0);
                if (rejected == null) {
                    rejected = new HashSet<>();
                }
                rejected.add(group.id());
            }
        }

        return Optional.empty();
    }

    private void emitDispatchMetric(Optional<SearchInvoker> invoker) {
        if (invoker.isEmpty()) {
            metric.add(FDISPATCH_METRIC, 1, metricContext);
        } else {
            metric.add(INTERNAL_METRIC, 1, metricContext);
        }
    }
}
