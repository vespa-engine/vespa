// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.ChannelTimeoutException;
import com.yahoo.fs4.Packet;
import com.yahoo.fs4.QueryPacket;
import com.yahoo.fs4.QueryResultPacket;
import com.yahoo.fs4.mplex.FS4Channel;
import com.yahoo.fs4.mplex.InvalidChannelException;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.ResponseMonitor;
import com.yahoo.search.dispatch.SearchInvoker;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link SearchInvoker} implementation for FS4 nodes and fdispatch
 *
 * @author ollivir
 */
public class FS4SearchInvoker extends SearchInvoker implements ResponseMonitor<FS4Channel> {

    private final VespaBackEndSearcher searcher;
    private FS4Channel channel;

    private ErrorMessage pendingSearchError = null;
    private Query query = null;
    private QueryPacket queryPacket = null;

    public FS4SearchInvoker(VespaBackEndSearcher searcher, Query query, FS4Channel channel, Optional<Node> node) {
        super(node);
        this.searcher = searcher;
        this.channel = channel;

        channel.setQuery(query);
        channel.setResponseMonitor(this);
    }

    @Override
    protected void sendSearchRequest(Query query, QueryPacket queryPacket) throws IOException {
        if (isLoggingFine())
            getLogger().finest("sending query packet");

        if (queryPacket == null) {
            // query changed for subchannel
            queryPacket = searcher.createQueryPacket(searcher.getServerId(), query);
        }

        this.query = query;
        this.queryPacket = queryPacket;

        try {
            boolean couldSend = channel.sendPacket(queryPacket);
            if (!couldSend) {
                setPendingError("Could not reach '" + getName() + "'");
            }
        } catch (InvalidChannelException e) {
            setPendingError("Invalid channel " + getName());
        } catch (IllegalStateException e) {
            setPendingError("Illegal state in FS4: " + e.getMessage());
        }
    }

    private void setPendingError(String message) {
        pendingSearchError = ErrorMessage.createBackendCommunicationError(message);
        responseAvailable();
    }

    @Override
    protected Result getSearchResult(CacheKey cacheKey, Execution execution) throws IOException {
        if (pendingSearchError != null) {
            return errorResult(pendingSearchError);
        }
        BasicPacket[] basicPackets;

        try {
            basicPackets = channel.receivePackets(query.getTimeLeft(), 1);
        } catch (ChannelTimeoutException e) {
            return errorResult(ErrorMessage.createTimeout("Timeout while waiting for " + getName()));
        } catch (InvalidChannelException e) {
            return errorResult(ErrorMessage.createBackendCommunicationError("Invalid channel for " + getName()));
        }

        if (basicPackets.length == 0) {
            return errorResult(ErrorMessage.createBackendCommunicationError(getName() + " got no packets back"));
        }

        if (isLoggingFine())
            getLogger().finest("got packets " + basicPackets.length + " packets");

        basicPackets[0].ensureInstanceOf(QueryResultPacket.class, getName());
        QueryResultPacket resultPacket = (QueryResultPacket) basicPackets[0];

        if (isLoggingFine())
            getLogger().finest("got query packet. " + "docsumClass=" + query.getPresentation().getSummary());

        if (query.getPresentation().getSummary() == null)
            query.getPresentation().setSummary(searcher.getDefaultDocsumClass());

        Result result = new Result(query);

        searcher.addMetaInfo(query, queryPacket.getQueryPacketData(), resultPacket, result);

        searcher.addUnfilledHits(result, resultPacket.getDocuments(), false, queryPacket.getQueryPacketData(), cacheKey, distributionKey());
        Packet[] packets;
        CacheControl cacheControl = searcher.getCacheControl();
        PacketWrapper packetWrapper = cacheControl.lookup(cacheKey, query);

        if (packetWrapper != null) {
            cacheControl.updateCacheEntry(cacheKey, query, resultPacket);
        } else {
            if (resultPacket.getCoverageFeature() && !resultPacket.getCoverageFull()) {
                // Don't add error here, it was done in first phase
                // No check if packetWrapper already exists, since incomplete
                // first phase data won't be cached anyway.
            } else {
                packets = new Packet[1];
                packets[0] = resultPacket;
                cacheControl.cache(cacheKey, query, new DocsumPacketKey[0], packets, distributionKey());
            }
        }
        return result;
    }

    private Result errorResult(ErrorMessage errorMessage) {
        Result error = new Result(query, errorMessage);
        Coverage errorCoverage = new Coverage(0, 0, 0);
        errorCoverage.setNodesTried(1);
        error.setCoverage(errorCoverage);
        return error;
    }

    @Override
    public void release() {
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    private String getName() {
        return searcher.getName();
    }

    private Logger getLogger() {
        return searcher.getLogger();
    }

    private boolean isLoggingFine() {
        return getLogger().isLoggable(Level.FINE);
    }

    @Override
    public void responseAvailable(FS4Channel from) {
        responseAvailable();
    }

}
