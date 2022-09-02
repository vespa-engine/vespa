// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.dispatch.rpc;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.google.protobuf.ByteString;
import com.yahoo.document.GlobalId;
import com.yahoo.document.idstring.IdString;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Query;
import com.yahoo.search.dispatch.InvokerResult;
import com.yahoo.search.dispatch.LeanHit;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfileXMLReader;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author ollivir
 */
public class ProtobufSerializationTest {

    static final double DELTA = 0.000000000001;

    @Test
    void testQuerySerialization() {
        CompiledQueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/tensortypes").compile();
        Query query = new Query.Builder().setQueryProfile(registry.getComponent("profile1"))
                .setRequest("?query=test&ranking.features.query(tensor_1)=[1.200]")
                .build();

        SearchProtocol.SearchRequest request1 = ProtobufSerialization.convertFromQuery(query, 9, "serverId", 0.5);
        assertEquals(9, request1.getHits());
        assertEquals(0, request1.getRankPropertiesCount());
        assertEquals(0, request1.getTensorRankPropertiesCount());
        assertEquals(0, request1.getFeatureOverridesCount());
        assertEquals(2, request1.getTensorFeatureOverridesCount());
        assertEquals("\"\\001\\001\\003key\\001\\rpre_key1_post?\\360\\000\\000\\000\\000\\000\\000\"",
                contentsOf(request1.getTensorFeatureOverrides(0).getValue()));
        assertEquals("\"\\006\\001\\001\\001x\\001?\\231\\231\\232\"",
                contentsOf(request1.getTensorFeatureOverrides(1).getValue()));

        query.prepare(); // calling prepare() moves "overrides" to "features" - content stays the same
        SearchProtocol.SearchRequest request2 = ProtobufSerialization.convertFromQuery(query, 9, "serverId", 0.5);
        assertEquals(9, request2.getHits());
        assertEquals(0, request2.getRankPropertiesCount());
        assertEquals(2, request2.getTensorRankPropertiesCount());
        assertEquals("\"\\001\\001\\003key\\001\\rpre_key1_post?\\360\\000\\000\\000\\000\\000\\000\"",
                contentsOf(request2.getTensorRankProperties(0).getValue()));
        assertEquals("\"\\006\\001\\001\\001x\\001?\\231\\231\\232\"",
                contentsOf(request2.getTensorRankProperties(1).getValue()));
        assertEquals(0, request2.getFeatureOverridesCount());
        assertEquals(0, request2.getTensorFeatureOverridesCount());
    }

    @Test
    void testDocsumSerialization() {
        Query q = new Query("search/?query=test&hits=10&offset=3");
        var builder = ProtobufSerialization.createDocsumRequestBuilder(q, "server", "summary", Set.of("f1", "f2"),true, 0.5);
        builder.setTimeout(0);
        var hit = new FastHit();
        hit.setGlobalId(new GlobalId(IdString.createIdString("id:ns:type::id")).getRawId());
        var bytes = ProtobufSerialization.serializeDocsumRequest(builder, Collections.singletonList(hit));

        assertEquals(56, bytes.length);
    }

    private String contentsOf(ByteString property) {
        String string = property.toString();
        int contentIndex = string.indexOf("contents=");
        return string.substring(contentIndex + "contents=".length(), string.length() - 1);
    }

    SearchProtocol.SearchReply createSearchReply(int numHits, boolean useSorting) {
        SearchProtocol.SearchReply.Builder reply = SearchProtocol.SearchReply.newBuilder();
        reply.setTotalHitCount(7);

        for (int i = 0; i < numHits; i++) {
            SearchProtocol.Hit.Builder hit = SearchProtocol.Hit.newBuilder();
            byte [] gid = {'a','a','a','a','a','a','a','a','a','a','a', (byte)i};
            hit.setGlobalId(ByteString.copyFrom(gid));
            if (useSorting) {
                gid[0] = 'b';
                hit.setSortData(ByteString.copyFrom(gid));
            } else {
                hit.setRelevance(numHits - i);
            }
            reply.addHits(hit);
        }
        return reply.build();
    }

    @Test
    void testSearchReplyDecodingWithRelevance() {
        Query q = new Query("search/?query=test");
        InvokerResult result = ProtobufSerialization.convertToResult(q, createSearchReply(5, false), null, 1, 2);
        assertEquals(result.getResult().getTotalHitCount(), 7);
        List<LeanHit> hits = result.getLeanHits();
        assertEquals(5, hits.size());
        double expectedRelevance = 5;
        int hitNum = 0;
        for (LeanHit hit : hits) {
            assertEquals('a', hit.getGid()[0]);
            assertEquals(hitNum, hit.getGid()[11]);
            assertEquals(expectedRelevance--, hit.getRelevance(), DELTA);
            assertEquals(1, hit.getPartId());
            assertEquals(2, hit.getDistributionKey());
            assertFalse(hit.hasSortData());
            hitNum++;
        }
    }

    @Test
    void testSearchReplyDecodingWithSortData() {
        Query q = new Query("search/?query=test");
        InvokerResult result = ProtobufSerialization.convertToResult(q, createSearchReply(5, true), null, 1, 2);
        assertEquals(result.getResult().getTotalHitCount(), 7);
        List<LeanHit> hits = result.getLeanHits();
        assertEquals(5, hits.size());
        int hitNum = 0;
        for (LeanHit hit : hits) {
            assertEquals('a', hit.getGid()[0]);
            assertEquals(hitNum, hit.getGid()[11]);
            assertEquals(0.0, hit.getRelevance(), DELTA);
            assertEquals(1, hit.getPartId());
            assertEquals(2, hit.getDistributionKey());
            assertTrue(hit.hasSortData());
            assertEquals('b', hit.getSortData()[0]);
            assertEquals(hitNum, hit.getSortData()[11]);
            hitNum++;
        }
    }

}
