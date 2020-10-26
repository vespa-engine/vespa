// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.dispatch.rpc;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.google.protobuf.ByteString;
import com.yahoo.document.GlobalId;
import com.yahoo.document.idstring.IdString;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Query;
import com.yahoo.search.dispatch.InvokerResult;
import com.yahoo.search.dispatch.LeanHit;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author ollivir
 */
public class ProtobufSerializationTest {
    static final double DELTA = 0.000000000001;

    @Test
    public void testDocsumSerialization() throws IOException {
        Query q = new Query("search/?query=test&hits=10&offset=3");
        var builder = ProtobufSerialization.createDocsumRequestBuilder(q, "server", "summary", true);
        builder.setTimeout(0);
        var hit = new FastHit();
        hit.setGlobalId(new GlobalId(IdString.createIdString("id:ns:type::id")).getRawId());
        var bytes = ProtobufSerialization.serializeDocsumRequest(builder, Collections.singletonList(hit));

        assertThat(bytes.length, equalTo(41));
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
    public void testSearhReplyDecodingWithRelevance() {
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
    public void testSearhReplyDecodingWithSortData() {
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
