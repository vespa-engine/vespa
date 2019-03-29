// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.prelude.Pong;
import com.yahoo.prelude.fastsearch.DocumentDatabase;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.dispatch.FillInvoker;
import com.yahoo.search.dispatch.InvokerFactory;
import com.yahoo.search.dispatch.SearchInvoker;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * @author ollivir
 */
public class RpcInvokerFactory extends InvokerFactory {
    /** Unless turned off this will fill summaries by dispatching directly to search nodes over RPC when possible */
    private final static CompoundName dispatchSummaries = new CompoundName("dispatch.summaries");

    private final RpcResourcePool rpcResourcePool;
    private final boolean dispatchWithProtobuf;

    public RpcInvokerFactory(RpcResourcePool rpcResourcePool, SearchCluster searchCluster, boolean dispatchWithProtobuf) {
        super(searchCluster);
        this.rpcResourcePool = rpcResourcePool;
        this.dispatchWithProtobuf = dispatchWithProtobuf;
    }

    @Override
    protected Optional<SearchInvoker> createNodeSearchInvoker(VespaBackEndSearcher searcher, Query query, Node node) {
        return Optional.of(new RpcSearchInvoker(searcher, node, rpcResourcePool));
    }

    @Override
    public Optional<FillInvoker> createFillInvoker(VespaBackEndSearcher searcher, Result result) {
        Query query = result.getQuery();

        boolean summaryNeedsQuery = searcher.summaryNeedsQuery(query);

        if(query.properties().getBoolean(Dispatcher.dispatchProtobuf, dispatchWithProtobuf)) {
            return Optional.of(new RpcProtobufFillInvoker(rpcResourcePool, searcher.getDocumentDatabase(query), searcher.getServerId(),
                    summaryNeedsQuery));
        }
        if (query.properties().getBoolean(dispatchSummaries, true)
                && ! summaryNeedsQuery
                && query.getRanking().getLocation() == null)
        {
            return Optional.of(new RpcFillInvoker(rpcResourcePool, searcher.getDocumentDatabase(query)));
        } else {
            return Optional.empty();
        }
    }

    // for testing
    public FillInvoker createFillInvoker(DocumentDatabase documentDb) {
        return new RpcFillInvoker(rpcResourcePool, documentDb);
    }

    public void release() {
        rpcResourcePool.release();
    }

    @Override
    public Callable<Pong> createPinger(Node node, ClusterMonitor<Node> monitor) {
        return new RpcPing(node, monitor, rpcResourcePool);
    }
}
