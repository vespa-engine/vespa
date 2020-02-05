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
import com.yahoo.search.dispatch.searchcluster.PingFactory;
import com.yahoo.search.dispatch.searchcluster.Pinger;
import com.yahoo.search.dispatch.searchcluster.PongHandler;
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

    public RpcInvokerFactory(RpcResourcePool rpcResourcePool, SearchCluster searchCluster) {
        super(searchCluster);
        this.rpcResourcePool = rpcResourcePool;
    }

    @Override
    protected Optional<SearchInvoker> createNodeSearchInvoker(VespaBackEndSearcher searcher,
                                                              Query query,
                                                              int maxHits,
                                                              Node node) {
        return Optional.of(new RpcSearchInvoker(searcher, node, rpcResourcePool, maxHits));
    }

    @Override
    public FillInvoker createFillInvoker(VespaBackEndSearcher searcher, Result result) {
        Query query = result.getQuery();

        boolean summaryNeedsQuery = searcher.summaryNeedsQuery(query);
        boolean useProtoBuf = query.properties().getBoolean(Dispatcher.dispatchProtobuf, true);
        boolean useDispatchDotSummaries = query.properties().getBoolean(dispatchSummaries, false);

        return  ((useDispatchDotSummaries || !useProtoBuf) && ! summaryNeedsQuery)
                ? new RpcFillInvoker(rpcResourcePool, searcher.getDocumentDatabase(query))
                : new RpcProtobufFillInvoker(rpcResourcePool, searcher.getDocumentDatabase(query), searcher.getServerId(), summaryNeedsQuery);
    }

    // for testing
    public FillInvoker createFillInvoker(DocumentDatabase documentDb) {
        return new RpcFillInvoker(rpcResourcePool, documentDb);
    }

    public void release() {
        rpcResourcePool.release();
    }
}
