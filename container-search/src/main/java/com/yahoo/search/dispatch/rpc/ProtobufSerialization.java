// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol.StringProperty;
import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol.TensorProperty;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.yahoo.data.access.simple.Value;
import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.fs4.GetDocSumsPacket;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.prelude.fastsearch.DocumentDatabase;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.InvokerResult;
import com.yahoo.search.dispatch.LeanHit;
import com.yahoo.search.grouping.vespa.GroupingExecutor;
import com.yahoo.search.query.Model;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.Sorting;
import com.yahoo.search.query.Sorting.Order;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.slime.BinaryFormat;
import com.yahoo.vespa.objects.BufferSerializer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ProtobufSerialization {

    /*
     * This is a thread local buffer that is used as scratchpad during serialization.
     * - avoids the unnecessary cost of allocating and initializing a buffer that is too large.
     * - avoids resizing for large queries.
     * - Reduces garbage creation.
     * There is a limited number of threads that will use this so the upper bound should be fine.
     */
    private static final ThreadLocal<GrowableByteBuffer> threadLocalBuffer = ThreadLocal.withInitial(() -> new GrowableByteBuffer(4096));

    static byte[] serializeSearchRequest(Query query, int hits, String serverId, double requestTimeout) {
        return convertFromQuery(query, hits, serverId, requestTimeout).toByteArray();
    }

    private static void convertSearchReplyErrors(Result target, List<SearchProtocol.Error> errors) {
        for (var error : errors) {
            target.hits().addError(ErrorMessage.createSearchReplyError(error.getMessage()));
        }
    }

    static SearchProtocol.SearchRequest convertFromQuery(Query query, int hits, String serverId, double requestTimeout) {
        var builder = SearchProtocol.SearchRequest.newBuilder().setHits(hits).setOffset(query.getOffset())
                .setTimeout((int) (requestTimeout * 1000));

        var documentDb = query.getModel().getDocumentDb();
        if (documentDb != null) {
            builder.setDocumentType(documentDb);
        }
        GrowableByteBuffer scratchPad = threadLocalBuffer.get();
        builder.setQueryTreeBlob(serializeQueryTree(query.getModel().getQueryTree(), scratchPad));

        if (query.getGroupingSessionCache() || query.getRanking().getQueryCache()) {
            // TODO verify that the session key is included whenever rank properties would have been
            builder.setSessionKey(query.getSessionId(serverId).toString());
        }
        if (query.properties().getBoolean(Model.ESTIMATE)) {
            builder.setHits(0);
        }
        if (GroupingExecutor.hasGroupingList(query)) {
            List<Grouping> groupingList = GroupingExecutor.getGroupingList(query);
            scratchPad.clear();
            BufferSerializer gbuf = new BufferSerializer(scratchPad);
            gbuf.putInt(null, groupingList.size());
            for (Grouping g : groupingList) {
                g.serialize(gbuf);
            }
            gbuf.getBuf().flip();
            builder.setGroupingBlob(ByteString.copyFrom(gbuf.getBuf().getByteBuffer()));
        }
        if (query.getGroupingSessionCache()) {
            builder.setCacheGrouping(true);
        }

        builder.setTraceLevel(getTraceLevelForBackend(query));
        builder.setProfileDepth(query.getTrace().getProfileDepth());

        mergeToSearchRequestFromRanking(query.getRanking(), scratchPad, builder);

        return builder.build();
    }

    public static int getTraceLevelForBackend(Query query) {
        int traceLevel = query.getTrace().getLevel();
        if (query.getModel().getExecution().trace().getForceTimestamps()) {
            traceLevel = Math.max(traceLevel, 5); // Backend produces timing information on level 4 and 5
        }
        if (query.getTrace().getExplainLevel() > 0) {
            traceLevel = Math.max(traceLevel, query.getTrace().getExplainLevel() + 5);
        }
        return traceLevel;
    }

    private static void mergeToSearchRequestFromRanking(Ranking ranking, GrowableByteBuffer scratchPad, SearchProtocol.SearchRequest.Builder builder) {
        builder.setRankProfile(ranking.getProfile());

        if (ranking.getQueryCache()) {
            builder.setCacheQuery(true);
        }
        if (ranking.getSorting() != null) {
            mergeToSearchRequestFromSorting(ranking.getSorting(), builder);
        }
        if (ranking.getLocation() != null) {
            builder.setGeoLocation(ranking.getLocation().backendString());
        }

        var featureMap = ranking.getFeatures().asMap();
        MapConverter.convertMapPrimitives(featureMap, builder::addFeatureOverrides);
        MapConverter.convertMapTensors(scratchPad, featureMap, builder::addTensorFeatureOverrides);
        mergeRankProperties(ranking, scratchPad, builder::addRankProperties, builder::addTensorRankProperties);
    }

    private static void mergeToSearchRequestFromSorting(Sorting sorting, SearchProtocol.SearchRequest.Builder builder) {
        for (var field : sorting.fieldOrders()) {
            var sortField = SearchProtocol.SortField.newBuilder()
                    .setField(field.getSorter().toSerialForm())
                    .setAscending(field.getSortOrder() == Order.ASCENDING).build();
            builder.addSorting(sortField);
        }
    }

    static SearchProtocol.DocsumRequest.Builder createDocsumRequestBuilder(Query query,
                                                                           String serverId,
                                                                           String summaryClass,
                                                                           Set<String> fields,
                                                                           boolean includeQueryData,
                                                                           double requestTimeout) {
        var builder = SearchProtocol.DocsumRequest.newBuilder()
                .setTimeout((int) (requestTimeout * 1000))
                .setDumpFeatures(query.properties().getBoolean(Ranking.RANKFEATURES, false));

        if (summaryClass != null) {
            builder.setSummaryClass(summaryClass);
        }
        if (fields != null) {
            builder.addAllFields(fields);
        }

        var documentDb = query.getModel().getDocumentDb();
        if (documentDb != null) {
            builder.setDocumentType(documentDb);
        }

        var ranking = query.getRanking();
        if (ranking.getQueryCache()) {
            builder.setCacheQuery(true);
            builder.setSessionKey(query.getSessionId(serverId).toString());
        }
        builder.setRankProfile(ranking.getProfile());

        if (ranking.getLocation() != null) {
            builder.setGeoLocation(ranking.getLocation().backendString());
        }
        GrowableByteBuffer scratchPad = threadLocalBuffer.get();
        if (includeQueryData) {
            mergeQueryDataToDocsumRequest(query, scratchPad, builder);
        }
        if (query.getTrace().getLevel() >= 3) {
            query.trace((includeQueryData ? "ProtoBuf: Resending " : "Not resending ") + "query during document summary fetching", 3);
        }

        return builder;
    }

    static byte[] serializeDocsumRequest(SearchProtocol.DocsumRequest.Builder builder, List<FastHit> documents) {
        builder.clearGlobalIds();
        for (var hit : documents) {
            builder.addGlobalIds(ByteString.copyFrom(hit.getRawGlobalId()));
        }
        return builder.build().toByteArray();
    }

    private static void mergeQueryDataToDocsumRequest(Query query, GrowableByteBuffer scratchPad, SearchProtocol.DocsumRequest.Builder builder) {
        var ranking = query.getRanking();
        var featureMap = ranking.getFeatures().asMap();

        builder.setQueryTreeBlob(serializeQueryTree(query.getModel().getQueryTree(), scratchPad));

        MapConverter.convertMapPrimitives(featureMap, builder::addFeatureOverrides);
        MapConverter.convertMapTensors(scratchPad, featureMap, builder::addTensorFeatureOverrides);
        if (query.getPresentation().getHighlight() != null) {
            MapConverter.convertStringMultiMap(query.getPresentation().getHighlight().getHighlightTerms(), builder::addHighlightTerms);
        }
        mergeRankProperties(ranking, scratchPad, builder::addRankProperties, builder::addTensorRankProperties);
    }
    static byte[] serializeResult(Result searchResult) {
        return convertFromResult(searchResult).toByteArray();
    }

    static InvokerResult deserializeToSearchResult(byte[] payload, Query query, VespaBackEndSearcher searcher, int partId, int distKey)
            throws InvalidProtocolBufferException {
        var protobuf = SearchProtocol.SearchReply.parseFrom(payload);
        return convertToResult(query, protobuf, searcher.getDocumentDatabase(query), partId, distKey);
    }

    static InvokerResult convertToResult(Query query, SearchProtocol.SearchReply protobuf,
                                         DocumentDatabase documentDatabase, int partId, int distKey)
    {
        InvokerResult result = new InvokerResult(query, protobuf.getHitsCount());

        result.getResult().setTotalHitCount(protobuf.getTotalHitCount());
        result.getResult().setCoverage(convertToCoverage(protobuf));

        convertSearchReplyErrors(result.getResult(), protobuf.getErrorsList());
        List<String> featureNames = protobuf.getMatchFeatureNamesList();
        var haveMatchFeatures = ! featureNames.isEmpty();
        MatchFeatureData matchFeatures = haveMatchFeatures ? new MatchFeatureData(featureNames) : null;
        var haveGrouping = ! protobuf.getGroupingBlob().isEmpty();
        if (haveGrouping) {
            BufferSerializer buf = new BufferSerializer(new GrowableByteBuffer(protobuf.getGroupingBlob().asReadOnlyByteBuffer()));
            int cnt = buf.getInt(null);
            ArrayList<Grouping> list = new ArrayList<>(cnt);
            for (int i = 0; i < cnt; i++) {
                Grouping g = new Grouping();
                g.deserialize(buf);
                list.add(g);
            }
            GroupingListHit hit = new GroupingListHit(list, documentDatabase.getDocsumDefinitionSet());
            hit.setQuery(query);
            result.getResult().hits().add(hit);
        }
        for (var replyHit : protobuf.getHitsList()) {
            LeanHit hit = (replyHit.getSortData().isEmpty())
                    ? new LeanHit(replyHit.getGlobalId().toByteArray(), partId, distKey, replyHit.getRelevance())
                    : new LeanHit(replyHit.getGlobalId().toByteArray(), partId, distKey, replyHit.getRelevance(), replyHit.getSortData().toByteArray());
            if (haveMatchFeatures) {
                var hitFeatures = matchFeatures.addHit();
                var featureList = replyHit.getMatchFeaturesList();
                if (featureList.size() == featureNames.size()) {
                    int idx = 0;
                    for (SearchProtocol.Feature value : featureList) {
                        ByteString tensorBlob = value.getTensor();
                        if (tensorBlob.isEmpty()) {
                            hitFeatures.set(idx++, value.getNumber());
                        } else {
                            hitFeatures.set(idx++, tensorBlob.toByteArray());
                        }
                    }
                    hit.addMatchFeatures(hitFeatures);
                } else {
                    result.getResult().hits().addError(ErrorMessage.createBackendCommunicationError("mismatch in match feature sizes"));
                }
            }
            result.getLeanHits().add(hit);
        }

        var slimeTrace = protobuf.getSlimeTrace();
        if ( ! slimeTrace.isEmpty()) {
            var traces = new Value.ArrayValue();
            traces.add(new SlimeAdapter(BinaryFormat.decode(slimeTrace.toByteArray()).get()));
            query.trace(traces, query.getTrace().getLevel());
        }
        return result;
    }

    private static Coverage convertToCoverage(SearchProtocol.SearchReply protobuf) {
        var coverage = new Coverage(protobuf.getCoverageDocs(), protobuf.getActiveDocs(), 1);
        coverage.setNodesTried(1).setTargetActive(protobuf.getTargetActiveDocs());

        int degradedReason = 0;
        if (protobuf.getDegradedByMatchPhase())
            degradedReason |= Coverage.DEGRADED_BY_MATCH_PHASE;
        if (protobuf.getDegradedBySoftTimeout())
            degradedReason |= Coverage.DEGRADED_BY_TIMEOUT;
        coverage.setDegradedReason(degradedReason);

        return coverage;
    }

    private static SearchProtocol.SearchReply convertFromResult(Result result) {
        var builder = SearchProtocol.SearchReply.newBuilder();

        var coverage = result.getCoverage(false);
        if (coverage != null) {
            builder.setCoverageDocs(coverage.getDocs()).setActiveDocs(coverage.getActive()).setTargetActiveDocs(coverage.getTargetActive())
                    .setDegradedBySoftTimeout(coverage.isDegradedByTimeout()).setDegradedByMatchPhase(coverage.isDegradedByMatchPhase());
        }

        result.hits().iterator().forEachRemaining(hit -> {
            var hitBuilder = SearchProtocol.Hit.newBuilder();
            if (hit.getRelevance() != null) {
                hitBuilder.setRelevance(hit.getRelevance().getScore());
            }
            if (hit instanceof FastHit fhit) {
                hitBuilder.setGlobalId(ByteString.copyFrom(fhit.getRawGlobalId()));
            }
            builder.addHits(hitBuilder);
        });
        return builder.build();
    }

    private static ByteString serializeQueryTree(QueryTree queryTree, GrowableByteBuffer scratchPad) {
        while (true) {
            try {
                scratchPad.clear();
                ByteBuffer treeBuffer = scratchPad.getByteBuffer();
                queryTree.encode(treeBuffer);
                return ByteString.copyFrom(treeBuffer.flip());
            } catch (java.nio.BufferOverflowException e) {
                scratchPad.clear();
                scratchPad.grow(scratchPad.capacity()*2);
            }
        }
    }

    private static void mergeRankProperties(Ranking ranking,
                                            GrowableByteBuffer scratchPad,
                                            Consumer<StringProperty.Builder> stringProperties,
                                            Consumer<TensorProperty.Builder> tensorProperties) {
        MapConverter.convertMultiMap(scratchPad, ranking.getProperties().asMap(), propB -> {
            if (!GetDocSumsPacket.sessionIdKey.equals(propB.getName())) {
                stringProperties.accept(propB);
            }
        }, tensorProperties);
    }

}
