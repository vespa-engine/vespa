// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.FillInvoker;
import com.yahoo.search.dispatch.InvokerFactory;
import com.yahoo.search.dispatch.SearchInvoker;
import com.yahoo.search.dispatch.searchcluster.SearchGroups;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.vespa.config.search.DispatchConfig;

import java.util.Optional;

/**
 * @author ollivir
 */
public class RpcInvokerFactory extends InvokerFactory {

    private final RpcConnectionPool rpcResourcePool;
    private final CompressPayload compressor;
    private final RpcProtobufFillInvoker.DecodePolicy decodeType;

    private static RpcProtobufFillInvoker.DecodePolicy convert(DispatchConfig.SummaryDecodePolicy.Enum decoding) {
        return switch (decoding) {
            case EAGER -> RpcProtobufFillInvoker.DecodePolicy.EAGER;
            case ONDEMAND -> RpcProtobufFillInvoker.DecodePolicy.ONDEMAND;
        };
    }

    public RpcInvokerFactory(RpcConnectionPool rpcResourcePool, SearchGroups cluster, DispatchConfig dispatchConfig) {
        super(cluster, dispatchConfig);
        this.rpcResourcePool = rpcResourcePool;
        this.compressor = new CompressService();
        decodeType = convert(dispatchConfig.summaryDecodePolicy());
    }

    @Override
    protected Optional<SearchInvoker> createNodeSearchInvoker(VespaBackEndSearcher searcher, Query query, int maxHits, Node node) {
        return Optional.of(new RpcSearchInvoker(searcher, compressor, node, rpcResourcePool, maxHits));
    }

    @Override
    public FillInvoker createFillInvoker(VespaBackEndSearcher searcher, Result result) {
        Query query = result.getQuery();

        boolean summaryNeedsQuery = searcher.summaryNeedsQuery(query);
        return new RpcProtobufFillInvoker(rpcResourcePool, compressor, searcher.getDocumentDatabase(query),
                                          searcher.getServerId(), decodeType, summaryNeedsQuery);
    }
}
