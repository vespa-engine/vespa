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
import com.yahoo.vespa.config.search.DispatchConfig;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    /** If enabled, this internal dispatcher will be preferred over fdispatch whenever possible */
    private static final CompoundName dispatchInternal = new CompoundName("dispatch.internal");

    /** A model of the search cluster this dispatches to */
    private final SearchCluster searchCluster;

    private final LoadBalancer loadBalancer;
    private final RpcResourcePool rpcResourcePool;

    public Dispatcher(DispatchConfig dispatchConfig, FS4ResourcePool fs4ResourcePool, int containerClusterSize, VipStatus vipStatus) {
        this.searchCluster = new SearchCluster(dispatchConfig, fs4ResourcePool, containerClusterSize, vipStatus);
        this.loadBalancer = new LoadBalancer(searchCluster);
        this.rpcResourcePool = new RpcResourcePool(dispatchConfig);
    }

    /** For testing */
    public Dispatcher(Map<Integer, Client.NodeConnection> nodeConnections, Client client) {
        this.searchCluster = null;
        this.loadBalancer = new LoadBalancer(searchCluster);
        this.rpcResourcePool = new RpcResourcePool(client, nodeConnections);
    }

    /** Returns the search cluster this dispatches to */
    public SearchCluster searchCluster() {
        return searchCluster;
    }

    @Override
    public void deconstruct() {
        rpcResourcePool.release();
    }

    @FunctionalInterface
    private interface SearchInvokerSupplier {
        Optional<SearchInvoker> supply(Query query, List<SearchCluster.Node> nodes);
    }

    public Optional<FillInvoker> getFillInvoker(Result result, VespaBackEndSearcher searcher, DocumentDatabase documentDb,
            FS4InvokerFactory fs4InvokerFactory) {
        Optional<FillInvoker> rpcInvoker = rpcResourcePool.getFillInvoker(result.getQuery(), searcher, documentDb);
        if (rpcInvoker.isPresent()) {
            return rpcInvoker;
        }
        if (result.getQuery().properties().getBoolean(dispatchInternal, false)) {
            Optional<FillInvoker> fs4Invoker = fs4InvokerFactory.getFillInvoker(result);
            if (fs4Invoker.isPresent()) {
                return fs4Invoker;
            }
        }
        return Optional.empty();
    }

    public Optional<SearchInvoker> getSearchInvoker(Query query, FS4InvokerFactory fs4InvokerFactory) {
        if (query.properties().getBoolean(dispatchInternal, false)) {
            String searchPath = query.getModel().getSearchPath();
            if (searchPath != null) {
                Optional<SearchInvoker> invoker = getSearchPathInvoker(query, searchPath, fs4InvokerFactory::getSearchInvoker);
                if (invoker.isPresent()) {
                    return invoker;
                }
            }
            Optional<SearchInvoker> invoker = getInternalInvoker(query, fs4InvokerFactory::getSearchInvoker);
            return invoker;
        }
        return Optional.empty();
    }

    // build invoker based on searchpath
    private Optional<SearchInvoker> getSearchPathInvoker(Query query, String searchPath, SearchInvokerSupplier invokerFactory) {
        try {
            List<SearchCluster.Node> nodes = SearchPath.selectNodes(searchPath, searchCluster);
            if (nodes.isEmpty()) {
                return Optional.empty();
            } else {
                return invokerFactory.supply(query, nodes);
            }
        } catch (InvalidSearchPathException e) {
            return Optional.of(new SearchErrorInvoker(e.getMessage()));
        }
    }

    private Optional<SearchInvoker> getInternalInvoker(Query query, SearchInvokerSupplier invokerFactory) {
        Optional<SearchCluster.Node> directNode = searchCluster.directDispatchTarget();
        if (directNode.isPresent()) {
            SearchCluster.Node node = directNode.get();
            query.trace(false, 2, "Dispatching directly to ", node);
            return invokerFactory.supply(query, Arrays.asList(node));
        }

        Set<Integer> tried = null;
        int max = searchCluster.groups().size();
        for (int attempt = 0; attempt < max; attempt++) {
            Optional<SearchCluster.Group> groupInCluster = loadBalancer.takeGroupForQuery(query);
            if (! groupInCluster.isPresent()) {
                // No groups available
                break;
            }
            SearchCluster.Group group = groupInCluster.get();
            if (tried != null && tried.contains(group.id())) {
                // bail out: LB is offering a previously discarded group
                loadBalancer.releaseGroup(group);
                break;
            }

            Optional<SearchInvoker> invoker = invokerFactory.supply(query, group.nodes());
            if (invoker.isPresent()) {
                query.trace(false, 2, "Dispatching internally to ", group);
                invoker.get().teardown(() -> loadBalancer.releaseGroup(group));
                return invoker;
            } else {
                // invoker could not be produced (likely connectivity issue)
                loadBalancer.releaseGroup(group);
                if (tried == null) {
                    tried = new HashSet<>();
                }
                tried.add(group.id());
            }
        }

        return Optional.empty();
    }
}
