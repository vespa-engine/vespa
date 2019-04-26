// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.data.access.simple.Value;
import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.ChannelTimeoutException;
import com.yahoo.fs4.DocumentInfo;
import com.yahoo.fs4.FS4Properties;
import com.yahoo.fs4.QueryPacket;
import com.yahoo.fs4.QueryPacketData;
import com.yahoo.fs4.QueryResultPacket;
import com.yahoo.fs4.mplex.FS4Channel;
import com.yahoo.fs4.mplex.InvalidChannelException;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.log.LogLevel;
import com.yahoo.prelude.ConfigurationException;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.ResponseMonitor;
import com.yahoo.search.dispatch.SearchInvoker;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.query.Sorting;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.slime.BinaryFormat;
import com.yahoo.vespa.objects.BufferSerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * {@link SearchInvoker} implementation for FS4 nodes and fdispatch
 *
 * @author ollivir
 */
public class FS4SearchInvoker extends SearchInvoker implements ResponseMonitor<FS4Channel> {
    static final CompoundName PACKET_COMPRESSION_LIMIT = new CompoundName("packetcompressionlimit");
    static final CompoundName PACKET_COMPRESSION_TYPE = new CompoundName("packetcompressiontype");

    private static final Logger log = Logger.getLogger(FS4SearchInvoker.class.getName());

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
    protected void sendSearchRequest(Query query) throws IOException {
        log.finest("sending query packet");

        this.query = query;
        createQueryPacket(searcher.getServerId(), query);

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
    protected Result getSearchResult(Execution execution) throws IOException {
        if (pendingSearchError != null) {
            return errorResult(query, pendingSearchError);
        }
        BasicPacket[] basicPackets;

        try {
            basicPackets = channel.receivePackets(query.getTimeLeft(), 1);
        } catch (ChannelTimeoutException e) {
            return errorResult(query, ErrorMessage.createTimeout("Timeout while waiting for " + getName()));
        } catch (InvalidChannelException e) {
            return errorResult(query, ErrorMessage.createBackendCommunicationError("Invalid channel for " + getName()));
        }

        if (basicPackets.length == 0) {
            return errorResult(query, ErrorMessage.createBackendCommunicationError(getName() + " got no packets back"));
        }

        log.finest(() -> "got packets " + basicPackets.length + " packets");

        basicPackets[0].ensureInstanceOf(QueryResultPacket.class, getName());
        QueryResultPacket resultPacket = (QueryResultPacket) basicPackets[0];

        log.finest(() -> "got query packet. " + "docsumClass=" + query.getPresentation().getSummary());

        if (query.getPresentation().getSummary() == null)
            query.getPresentation().setSummary(searcher.getDefaultDocsumClass());

        Result result = new Result(query);

        ensureResultHitCapacity(result, resultPacket);
        addMetaInfo(query, queryPacket.getQueryPacketData(), resultPacket, result);
        addUnfilledHits(result, resultPacket.getDocuments(), queryPacket.getQueryPacketData());

        return result;
    }

    private QueryPacket createQueryPacket(String serverId, Query query) {
        this.queryPacket = QueryPacket.create(serverId, query);
        int compressionLimit = query.properties().getInteger(PACKET_COMPRESSION_LIMIT, 0);
        queryPacket.setCompressionLimit(compressionLimit);
        if (compressionLimit != 0) {
            queryPacket.setCompressionType(query.properties().getString(PACKET_COMPRESSION_TYPE, "lz4"));
        }
        log.fine(() -> "made QueryPacket: " + queryPacket);

        return queryPacket;
    }

    private void ensureResultHitCapacity(Result result, QueryResultPacket resultPacket) {
        int hitCount = resultPacket.getDocumentCount();
        if (resultPacket.getGroupData() != null) {
            hitCount++;
        }
        result.hits().ensureCapacity(hitCount);
    }

    private void addMetaInfo(Query query, QueryPacketData queryPacketData, QueryResultPacket resultPacket, Result result) {
        result.setTotalHitCount(resultPacket.getTotalDocumentCount());

        addBackendTrace(query, resultPacket);

        // Grouping
        if (resultPacket.getGroupData() != null) {
            byte[] data = resultPacket.getGroupData();
            ArrayList<Grouping> list = new ArrayList<>();
            BufferSerializer buf = new BufferSerializer(new GrowableByteBuffer(ByteBuffer.wrap(data)));
            int cnt = buf.getInt(null);
            for (int i = 0; i < cnt; i++) {
                Grouping g = new Grouping();
                g.deserialize(buf);
                list.add(g);
            }
            GroupingListHit hit = new GroupingListHit(list, searcher.getDocsumDefinitionSet(query));
            hit.setQuery(result.getQuery());
            hit.setSource(getName());
            hit.setQueryPacketData(queryPacketData);
            result.hits().add(hit);
        }

        if (resultPacket.getCoverageFeature()) {
            result.setCoverage(new Coverage(resultPacket.getCoverageDocs(), resultPacket.getActiveDocs(), resultPacket.getNodesReplied())
                    .setSoonActive(resultPacket.getSoonActiveDocs())
                    .setDegradedReason(resultPacket.getDegradedReason())
                    .setNodesTried(resultPacket.getNodesQueried()));
        }
    }

    private void addBackendTrace(Query query, QueryResultPacket resultPacket) {
        if (resultPacket.propsArray == null) return;
        Value.ArrayValue traces = new Value.ArrayValue();
        for (FS4Properties properties : resultPacket.propsArray) {
            if ( ! properties.getName().equals("trace")) continue;
            for (FS4Properties.Entry entry : properties.getEntries()) {
                traces.add(new SlimeAdapter(BinaryFormat.decode(entry.getValue()).get()));
            }
        }
        query.trace(traces, query.getTraceLevel());
    }

    /**
     * Creates unfilled hits from a List of DocumentInfo instances.
     */
    private void addUnfilledHits(Result result, List<DocumentInfo> documents, QueryPacketData queryPacketData) {
        Query myQuery = result.getQuery();
        Sorting sorting = myQuery.getRanking().getSorting();
        Optional<Integer> channelDistributionKey = distributionKey();

        for (DocumentInfo document : documents) {

            try {
                FastHit hit = new FastHit();
                hit.setQuery(myQuery);
                if (queryPacketData != null)
                    hit.setQueryPacketData(queryPacketData);

                hit.setFillable();
                hit.setCached(false);

                extractDocumentInfo(hit, document, sorting);
                channelDistributionKey.ifPresent(hit::setDistributionKey);

                result.hits().add(hit);
            } catch (ConfigurationException e) {
                log.log(LogLevel.WARNING, "Skipping hit", e);
            } catch (Exception e) {
                log.log(LogLevel.ERROR, "Skipping malformed hit", e);
            }
        }
    }

    private void extractDocumentInfo(FastHit hit, DocumentInfo document, Sorting sorting) {
        hit.setSource(getName());

        Number rank = document.getMetric();

        hit.setRelevance(new Relevance(rank.doubleValue()));

        hit.setDistributionKey(document.getDistributionKey());
        hit.setGlobalId(document.getGlobalId());
        hit.setPartId(document.getPartId());
        hit.setSortData(document.getSortData(), sorting);
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

    @Override
    public void responseAvailable(FS4Channel from) {
        responseAvailable();
    }

}
