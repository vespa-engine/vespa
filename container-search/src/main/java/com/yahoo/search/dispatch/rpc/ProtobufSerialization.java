package com.yahoo.search.dispatch.rpc;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol.SearchRequest.Builder;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.yahoo.document.GlobalId;
import com.yahoo.fs4.GetDocSumsPacket;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.prelude.fastsearch.DocumentDatabase;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.grouping.vespa.GroupingExecutor;
import com.yahoo.search.query.Model;
import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.Sorting;
import com.yahoo.search.query.Sorting.Order;
import com.yahoo.search.query.ranking.RankFeatures;
import com.yahoo.search.query.ranking.RankProperties;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.Relevance;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.vespa.objects.BufferSerializer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ProtobufSerialization {
    private static final int INITIAL_SERIALIZATION_BUFFER_SIZE = 10 * 1024;

    public static byte[] serializeQuery(Query query, String serverId, boolean includeQueryData) {
        return convertFromQuery(query, serverId, includeQueryData).toByteArray();
    }

    public static byte[] serializeResult(Result searchResult) {
        return convertFromResult(searchResult).toByteArray();
    }

    public static Result deserializeToResult(byte[] payload, Query query, VespaBackEndSearcher searcher)
            throws InvalidProtocolBufferException {
        var protobuf = SearchProtocol.SearchReply.parseFrom(payload);
        var result = convertToResult(query, protobuf, searcher.getDocumentDatabase(query));
        return result;
    }

    private static SearchProtocol.SearchRequest convertFromQuery(Query query, String serverId, boolean includeQueryData) {
        var builder = SearchProtocol.SearchRequest.newBuilder().setHits(query.getHits()).setOffset(query.getOffset())
                .setTimeout((int) query.getTimeLeft());

        mergeToRequestFromRanking(query.getRanking(), builder, includeQueryData);
        mergeToRequestFromModel(query.getModel(), builder);

        if (query.getGroupingSessionCache() || query.getRanking().getQueryCache()) {
            // TODO verify that the session key is included whenever rank properties would have been
            builder.setSessionKey(query.getSessionId(serverId).toString());
        }
        if (query.properties().getBoolean(Model.ESTIMATE)) {
            builder.setHits(0);
        }
        if (GroupingExecutor.hasGroupingList(query)) {
            List<Grouping> groupingList = GroupingExecutor.getGroupingList(query);
            BufferSerializer gbuf = new BufferSerializer(new GrowableByteBuffer());
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

        return builder.build();
    }

    private static void mergeToRequestFromModel(Model model, SearchProtocol.SearchRequest.Builder builder) {
        if (model.getDocumentDb() != null) {
            builder.setDocumentType(model.getDocumentDb());
        }
        int bufferSize = INITIAL_SERIALIZATION_BUFFER_SIZE;
        boolean success = false;
        while (!success) {
            try {
                ByteBuffer treeBuffer = ByteBuffer.allocate(bufferSize);
                model.getQueryTree().encode(treeBuffer);
                treeBuffer.flip();
                builder.setQueryTreeBlob(ByteString.copyFrom(treeBuffer));
                success = true;
            } catch (java.nio.BufferOverflowException e) {
                bufferSize *= 2;
            }
        }
    }

    private static void mergeToRequestFromSorting(Sorting sorting, SearchProtocol.SearchRequest.Builder builder, boolean includeQueryData) {
        for (var field : sorting.fieldOrders()) {
            var sortField = SearchProtocol.SortField.newBuilder().setField(field.getSorter().getName())
                    .setAscending(field.getSortOrder() == Order.ASCENDING).build();
            builder.addSorting(sortField);
        }
    }

    private static void mergeToRequestFromRanking(Ranking ranking, SearchProtocol.SearchRequest.Builder builder, boolean includeQueryData) {
        builder.setRankProfile(ranking.getProfile());
        if (ranking.getQueryCache()) {
            builder.setCacheQuery(true);
        }
        if (ranking.getSorting() != null) {
            mergeToRequestFromSorting(ranking.getSorting(), builder, includeQueryData);
        }
        if (ranking.getLocation() != null) {
            builder.setGeoLocation(ranking.getLocation().toString());
        }
        mergeToRequestFromRankFeatures(ranking.getFeatures(), builder, includeQueryData);
        mergeToRequestFromRankProperties(ranking.getProperties(), builder, includeQueryData);
    }

    private static void mergeToRequestFromRankFeatures(RankFeatures features, SearchProtocol.SearchRequest.Builder builder, boolean includeQueryData) {
        if (includeQueryData) {
            MapConverter.convertMapStrings(features.asMap(), builder::addFeatureOverrides);
            MapConverter.convertMapTensors(features.asMap(), builder::addTensorFeatureOverrides);
        }
    }

    private static void mergeToRequestFromRankProperties(RankProperties properties, Builder builder, boolean includeQueryData) {
        if (includeQueryData) {
            MapConverter.convertMultiMap(properties.asMap(), propB -> {
                if (!GetDocSumsPacket.sessionIdKey.equals(propB.getName())) {
                    builder.addRankProperties(propB);
                }
            }, builder::addTensorRankProperties);
        }
    }

    private static Result convertToResult(Query query, SearchProtocol.SearchReply protobuf, DocumentDatabase documentDatabase) {
        var result = new Result(query);

        result.setTotalHitCount(protobuf.getTotalHitCount());
        result.setCoverage(convertToCoverage(protobuf));

        if (protobuf.getGroupingBlob() != null && !protobuf.getGroupingBlob().isEmpty()) {
            ArrayList<Grouping> list = new ArrayList<>();
            BufferSerializer buf = new BufferSerializer(new GrowableByteBuffer(protobuf.getGroupingBlob().asReadOnlyByteBuffer()));
            int cnt = buf.getInt(null);
            for (int i = 0; i < cnt; i++) {
                Grouping g = new Grouping();
                g.deserialize(buf);
                list.add(g);
            }
            GroupingListHit hit = new GroupingListHit(list, documentDatabase.getDocsumDefinitionSet());
            hit.setQuery(query);
            result.hits().add(hit);
        }

        for (var replyHit : protobuf.getHitsList()) {
            FastHit hit = new FastHit();
            hit.setQuery(query);

            hit.setRelevance(new Relevance(replyHit.getRelevance()));
            hit.setGlobalId(new GlobalId(replyHit.getGlobalId().toByteArray()));

            hit.setFillable();
            hit.setCached(false);

            result.hits().add(hit);
        }

        return result;
    }

    private static Coverage convertToCoverage(SearchProtocol.SearchReply protobuf) {
        var coverage = new Coverage(protobuf.getCoverageDocs(), protobuf.getActiveDocs(), 1);
        coverage.setNodesTried(1).setSoonActive(protobuf.getSoonActiveDocs());

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
            builder.setCoverageDocs(coverage.getDocs()).setActiveDocs(coverage.getActive()).setSoonActiveDocs(coverage.getSoonActive())
                    .setDegradedBySoftTimeout(coverage.isDegradedByTimeout()).setDegradedByMatchPhase(coverage.isDegradedByMatchPhase());
        }

        result.hits().iterator().forEachRemaining(hit -> {
            var hitBuilder = SearchProtocol.Hit.newBuilder();
            if (hit.getRelevance() != null) {
                hitBuilder.setRelevance(hit.getRelevance().getScore());
            }
            if (hit instanceof FastHit) {
                FastHit fhit = (FastHit) hit;
                hitBuilder.setGlobalId(ByteString.copyFrom(fhit.getGlobalId().getRawId()));
            }
            builder.addHits(hitBuilder);
        });
        return builder.build();
    }

}
