// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.component.AbstractComponent;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.prelude.fastsearch.DocumentDatabase;
import com.yahoo.prelude.fastsearch.FS4InvokerFactory;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.SearchPath.InvalidSearchPathException;
import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.vespa.config.search.DispatchConfig;

import java.util.Arrays;
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
    private static final int MAX_GROUP_SELECTION_ATTEMPTS = 3;

    /** If enabled, this internal dispatcher will be preferred over fdispatch whenever possible */
    private static final CompoundName dispatchInternal = new CompoundName("dispatch.internal");

    /** A model of the search cluster this dispatches to */
    private final SearchCluster searchCluster;

    private final LoadBalancer loadBalancer;
    private final RpcResourcePool rpcResourcePool;
    private final boolean multilevelDispatch;
    private final boolean internalDispatchByDefault;

    public Dispatcher(String clusterId, DispatchConfig dispatchConfig, FS4ResourcePool fs4ResourcePool, int containerClusterSize, VipStatus vipStatus) {
        this(new SearchCluster(clusterId, dispatchConfig, fs4ResourcePool, containerClusterSize, vipStatus), dispatchConfig);
    }

    public Dispatcher(SearchCluster searchCluster, DispatchConfig dispatchConfig) {
        this.searchCluster = searchCluster;
        this.loadBalancer = new LoadBalancer(searchCluster,
                dispatchConfig.distributionPolicy() == DispatchConfig.DistributionPolicy.ROUNDROBIN);
        this.rpcResourcePool = new RpcResourcePool(dispatchConfig);
        this.multilevelDispatch = dispatchConfig.useMultilevelDispatch();
        this.internalDispatchByDefault = !dispatchConfig.useFdispatchByDefault();
    }

    /** Returns the search cluster this dispatches to */
    public SearchCluster searchCluster() {
        return searchCluster;
    }

    @Override
    public void deconstruct() {
        rpcResourcePool.release();
    }

    public Optional<FillInvoker> getFillInvoker(Result result, VespaBackEndSearcher searcher, DocumentDatabase documentDb,
            FS4InvokerFactory fs4InvokerFactory) {
        Optional<FillInvoker> rpcInvoker = rpcResourcePool.getFillInvoker(result.getQuery(), searcher, documentDb);
        if (rpcInvoker.isPresent()) {
            return rpcInvoker;
        }
        if (result.getQuery().properties().getBoolean(dispatchInternal, internalDispatchByDefault)) {
            Optional<FillInvoker> fs4Invoker = fs4InvokerFactory.getFillInvoker(result);
            if (fs4Invoker.isPresent()) {
                return fs4Invoker;
            }
        }
        return Optional.empty();
    }

    public Optional<SearchInvoker> getSearchInvoker(Query query, FS4InvokerFactory fs4InvokerFactory) {
        if (multilevelDispatch || ! query.properties().getBoolean(dispatchInternal, internalDispatchByDefault)) {
            return Optional.empty();
        }

        Optional<SearchInvoker> invoker = getSearchPathInvoker(query, fs4InvokerFactory::getSearchInvoker);

        if (!invoker.isPresent()) {
            invoker = getInternalInvoker(query, fs4InvokerFactory::getSearchInvoker);
        }
        if (invoker.isPresent() && query.properties().getBoolean(com.yahoo.search.query.Model.ESTIMATE)) {
            query.setHits(0);
            query.setOffset(0);
        }
        return invoker;
    }

    @FunctionalInterface
    private interface SearchInvokerSupplier {
        Optional<SearchInvoker> supply(Query query, int groupId, List<Node> nodes, boolean acceptIncompleteCoverage);
    }

    // build invoker based on searchpath
    private Optional<SearchInvoker> getSearchPathInvoker(Query query, SearchInvokerSupplier invokerFactory) {
        String searchPath = query.getModel().getSearchPath();
        if(searchPath == null) {
            return Optional.empty();
        }
        try {
            List<Node> nodes = SearchPath.selectNodes(searchPath, searchCluster);
            if (nodes.isEmpty()) {
                return Optional.empty();
            } else {
                query.trace(false, 2, "Dispatching internally with search path ", searchPath);
                return invokerFactory.supply(query, -1, nodes, true);
            }
        } catch (InvalidSearchPathException e) {
            return Optional.of(new SearchErrorInvoker(ErrorMessage.createIllegalQuery(e.getMessage())));
        }
    }

    private Optional<SearchInvoker> getInternalInvoker(Query query, SearchInvokerSupplier invokerFactory) {
        Optional<Node> directNode = searchCluster.directDispatchTarget();
        if (directNode.isPresent()) {
            Node node = directNode.get();
            query.trace(false, 2, "Dispatching directly to ", node);
            return invokerFactory.supply(query, -1, Arrays.asList(node), true);
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
            Optional<SearchInvoker> invoker = invokerFactory.supply(query, group.id(), group.nodes(), acceptIncompleteCoverage);
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
}
