// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test;

import java.util.Iterator;
import java.util.List;

import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.DocumentInfo;
import com.yahoo.fs4.QueryPacket;
import com.yahoo.fs4.QueryResultPacket;
import com.yahoo.search.Query;
import com.yahoo.prelude.fastsearch.CacheKey;
import com.yahoo.prelude.fastsearch.PacketWrapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests the logic wrapping cache entries.
 *
 * @author Steinar Knutsen
 */
public class PacketWrapperTestCase {

    @Test
    public void testPartialOverlap() {
        CacheKey key = new CacheKey(QueryPacket.create(new Query("/?query=key")));
        PacketWrapper w = createResult(key, 0, 10, 100);

        QueryResultPacket q = createQueryResultPacket(10, 10, 100);
        w.addResultPacket(q);

        // all docs at once
        List<?> l = w.getDocuments(0, 20);
        assertNotNull(l);
        assertEquals(20, l.size());
        int n = 0;
        for (Iterator<?> i = l.iterator(); i.hasNext(); ++n) {
            DocumentInfo d = (DocumentInfo) i.next();
            assertEquals(DocsumDefinitionTestCase.createGlobalId(n), d.getGlobalId());
        }

        // too far out into the result set
        l = w.getDocuments(15, 10);
        assertNull(l);

        // only from first subdivision
        l = w.getDocuments(3, 2);
        assertNotNull(l);
        assertEquals(2, l.size());
        n = 3;
        for (Iterator<?> i = l.iterator(); i.hasNext(); ++n) {
            DocumentInfo d = (DocumentInfo) i.next();
            assertEquals(DocsumDefinitionTestCase.createGlobalId(n), d.getGlobalId());
        }

        // only from second subdivision
        l = w.getDocuments(15, 5);
        assertNotNull(l);
        assertEquals(5, l.size());
        n = 15;
        for (Iterator<?> i = l.iterator(); i.hasNext(); ++n) {
            DocumentInfo d = (DocumentInfo) i.next();
            assertEquals(DocsumDefinitionTestCase.createGlobalId(n), d.getGlobalId());
        }

        // overshoot by 1
        l = w.getDocuments(15, 6);
        assertNull(l);

        // mixed subset
        l = w.getDocuments(3, 12);
        assertNotNull(l);
        assertEquals(12, l.size());
        n = 3;
        for (Iterator<?> i = l.iterator(); i.hasNext(); ++n) {
            DocumentInfo d = (DocumentInfo) i.next();
            assertEquals(DocsumDefinitionTestCase.createGlobalId(n), d.getGlobalId());
        }

    }

    @Test
    public void testPacketTrimming1() {
        CacheKey key = new CacheKey(QueryPacket.create(new Query("/?query=key")));
        PacketWrapper w = createResult(key, 0, 10, 100);

        QueryResultPacket q = createQueryResultPacket(5, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(10, 10, 100);
        w.addResultPacket(q);

        assertEquals(2, w.getResultPackets().size());
        List<?> l = w.getResultPackets();
        assertEquals(0, ((QueryResultPacket) l.get(0)).getOffset());
        assertEquals(10, ((QueryResultPacket) l.get(1)).getOffset());
    }

    @Test
    public void testPacketTrimming2() {
        CacheKey key = new CacheKey(QueryPacket.create(new Query("/?query=key")));
        PacketWrapper w = createResult(key, 0, 10, 100);

        QueryResultPacket q = createQueryResultPacket(5, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(50, 10, 100);
        w.addResultPacket(q);

        assertEquals(3, w.getResultPackets().size());
        List<?> l = w.getResultPackets();
        assertEquals(0, ((QueryResultPacket) l.get(0)).getOffset());
        assertEquals(5, ((QueryResultPacket) l.get(1)).getOffset());
        assertEquals(50, ((QueryResultPacket) l.get(2)).getOffset());
    }

    @Test
    public void testPacketTrimming3() {
        CacheKey key = new CacheKey(QueryPacket.create(new Query("/?query=key")));
        PacketWrapper w = createResult(key, 0, 10, 100);

        QueryResultPacket q = createQueryResultPacket(20, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(25, 10, 100);
        w.addResultPacket(q);

        assertEquals(3, w.getResultPackets().size());
        List<?> l = w.getResultPackets();
        assertEquals(0, ((QueryResultPacket) l.get(0)).getOffset());
        assertEquals(20, ((QueryResultPacket) l.get(1)).getOffset());
        assertEquals(25, ((QueryResultPacket) l.get(2)).getOffset());
    }

    @Test
    public void testPacketTrimming4() {
        CacheKey key = new CacheKey(QueryPacket.create(new Query("/?query=key")));
        PacketWrapper w = createResult(key, 0, 10, 100);

        QueryResultPacket q = createQueryResultPacket(5, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(10, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(15, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(20, 10, 100);
        w.addResultPacket(q);

        assertEquals(3, w.getResultPackets().size());
        List<?> l = w.getResultPackets();
        assertEquals(0, ((QueryResultPacket) l.get(0)).getOffset());
        assertEquals(10, ((QueryResultPacket) l.get(1)).getOffset());
        assertEquals(20, ((QueryResultPacket) l.get(2)).getOffset());
    }

    @Test
    public void testPacketTrimming5() {
        CacheKey key = new CacheKey(QueryPacket.create(new Query("/?query=key")));
        PacketWrapper w = createResult(key, 0, 10, 100);

        QueryResultPacket q = createQueryResultPacket(5, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(10, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(15, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(15, 85, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(20, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(25, 10, 100);
        w.addResultPacket(q);

        assertEquals(3, w.getResultPackets().size());
        List<?> l = w.getResultPackets();
        assertEquals(0, ((QueryResultPacket) l.get(0)).getOffset());
        assertEquals(10, ((QueryResultPacket) l.get(1)).getOffset());
        assertEquals(15, ((QueryResultPacket) l.get(2)).getOffset());
    }

    @Test
    public void testPacketTrimming6() {
        CacheKey key = new CacheKey(QueryPacket.create(new Query("/?query=key")));
        PacketWrapper w = createResult(key, 0, 10, 100);

        QueryResultPacket q = createQueryResultPacket(5, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(10, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(60, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(65, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(70, 10, 100);
        w.addResultPacket(q);

        assertEquals(4, w.getResultPackets().size());
        List<?> l = w.getResultPackets();
        assertEquals(0, ((QueryResultPacket) l.get(0)).getOffset());
        assertEquals(10, ((QueryResultPacket) l.get(1)).getOffset());
        assertEquals(60, ((QueryResultPacket) l.get(2)).getOffset());
        assertEquals(70, ((QueryResultPacket) l.get(3)).getOffset());
    }

    @Test
    public void testPacketTrimming7() {
        final Query query = new Query("/?query=key");
        query.setWindow(50, 10);
        CacheKey key = new CacheKey(QueryPacket.create(query));
        PacketWrapper w = createResult(key, 50, 10, 100);

        QueryResultPacket q = createQueryResultPacket(10, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(40, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(30, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(20, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(0, 10, 100);
        w.addResultPacket(q);

        assertEquals(6, w.getResultPackets().size());
        List<?> l = w.getResultPackets();
        assertEquals(0, ((QueryResultPacket) l.get(0)).getOffset());
        assertEquals(10, ((QueryResultPacket) l.get(1)).getOffset());
        assertEquals(20, ((QueryResultPacket) l.get(2)).getOffset());
        assertEquals(30, ((QueryResultPacket) l.get(3)).getOffset());
        assertEquals(40, ((QueryResultPacket) l.get(4)).getOffset());
        assertEquals(50, ((QueryResultPacket) l.get(5)).getOffset());
    }

    @Test
    public void testPacketTrimming8() {
        CacheKey key = new CacheKey(QueryPacket.create(new Query("/?query=key")));
        PacketWrapper w = createResult(key, 0, 10, 100);

        QueryResultPacket q = createQueryResultPacket(50, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(90, 10, 100);
        w.addResultPacket(q);

        assertEquals(3, w.getResultPackets().size());
        List<?> l = w.getResultPackets();
        assertEquals(0, ((QueryResultPacket) l.get(0)).getOffset());
        assertEquals(50, ((QueryResultPacket) l.get(1)).getOffset());
        assertEquals(90, ((QueryResultPacket) l.get(2)).getOffset());
    }

    @Test
    public void testPacketTrimming9() {
        CacheKey key = new CacheKey(QueryPacket.create(new Query("/?query=key")));
        PacketWrapper w = createResult(key, 0, 10, 100);

        QueryResultPacket q = createQueryResultPacket(10, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(11, 9, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(20, 10, 100);
        w.addResultPacket(q);

        assertEquals(3, w.getResultPackets().size());
        List<?> l = w.getResultPackets();
        assertEquals(0, ((QueryResultPacket) l.get(0)).getOffset());
        assertEquals(10, ((QueryResultPacket) l.get(1)).getOffset());
        assertEquals(20, ((QueryResultPacket) l.get(2)).getOffset());
    }

    @Test
    public void testPacketTrimming10() {
        CacheKey key = new CacheKey(QueryPacket.create(new Query("/?query=key")));
        PacketWrapper w = createResult(key, 0, 10, 100);

        QueryResultPacket q = createQueryResultPacket(0, 11, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(11, 9, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(20, 10, 100);
        w.addResultPacket(q);

        assertEquals(3, w.getResultPackets().size());
        List<?> l = w.getResultPackets();
        assertEquals(0, ((QueryResultPacket) l.get(0)).getOffset());
        assertEquals(11, ((QueryResultPacket) l.get(0)).getDocumentCount());
        assertEquals(11, ((QueryResultPacket) l.get(1)).getOffset());
        assertEquals(20, ((QueryResultPacket) l.get(2)).getOffset());
    }

    @Test
    public void testPacketTrimming11() {
        CacheKey key = new CacheKey(QueryPacket.create(new Query("/?query=key")));
        PacketWrapper w = createResult(key, 0, 10, 100);

        QueryResultPacket q = createQueryResultPacket(1, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(9, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(18, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(27, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(36, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(45, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(54, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(63, 10, 100);
        w.addResultPacket(q);

        assertEquals(8, w.getResultPackets().size());
        q = createQueryResultPacket(10, 90, 100);
        w.addResultPacket(q);
        assertEquals(2, w.getResultPackets().size());
    }

    @Test
    public void testPacketTrimming12() {
        CacheKey key = new CacheKey(QueryPacket.create(new Query("/?query=key")));
        PacketWrapper w = createResult(key, 0, 10, 100);

        QueryResultPacket q = createQueryResultPacket(4, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(12, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(16, 10, 100);
        w.addResultPacket(q);

        assertEquals(4, w.getResultPackets().size());
        q = createQueryResultPacket(8, 10, 100);
        w.addResultPacket(q);
        assertEquals(3, w.getResultPackets().size());
        List<?> l = w.getResultPackets();
        assertEquals(0, ((QueryResultPacket) l.get(0)).getOffset());
        assertEquals(8, ((QueryResultPacket) l.get(1)).getOffset());
        assertEquals(16, ((QueryResultPacket) l.get(2)).getOffset());
    }

    @Test
    public void testPacketTrimming13() {
        CacheKey key = new CacheKey(QueryPacket.create(new Query("/?query=key")));
        PacketWrapper w = createResult(key, 0, 10, 100);

        QueryResultPacket q = createQueryResultPacket(4, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(12, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(16, 10, 100);
        w.addResultPacket(q);

        assertEquals(4, w.getResultPackets().size());
        q = createQueryResultPacket(11, 10, 100);
        w.addResultPacket(q);
        assertEquals(4, w.getResultPackets().size());
        List<?> l = w.getResultPackets();
        assertEquals(0, ((QueryResultPacket) l.get(0)).getOffset());
        assertEquals(4, ((QueryResultPacket) l.get(1)).getOffset());
        assertEquals(12, ((QueryResultPacket) l.get(2)).getOffset());
        assertEquals(16, ((QueryResultPacket) l.get(3)).getOffset());
    }

    @Test
    public void testPacketTrimming14() {
        CacheKey key = new CacheKey(QueryPacket.create(new Query("/?query=key")));
        PacketWrapper w = createResult(key, 0, 10, 100);

        QueryResultPacket q = createQueryResultPacket(4, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(12, 10, 100);
        w.addResultPacket(q);
        q = createQueryResultPacket(16, 10, 100);
        w.addResultPacket(q);

        assertEquals(4, w.getResultPackets().size());
        q = createQueryResultPacket(5, 6, 100);
        w.addResultPacket(q);
        assertEquals(4, w.getResultPackets().size());
        List<?> l = w.getResultPackets();
        assertEquals(0, ((QueryResultPacket) l.get(0)).getOffset());
        assertEquals(4, ((QueryResultPacket) l.get(1)).getOffset());
        assertEquals(12, ((QueryResultPacket) l.get(2)).getOffset());
        assertEquals(16, ((QueryResultPacket) l.get(3)).getOffset());
    }

    @Test
    public void testZeroHits() {
        CacheKey key = new CacheKey(QueryPacket.create(new Query("/?query=key")));
        PacketWrapper w = createResult(key, 0, 10, 0);

        final Query query = new Query("/?query=key");
        query.setWindow(5, 10);
        key = new CacheKey(QueryPacket.create(query));

        QueryResultPacket q = createQueryResultPacket(5, 10, 0);
        w.addResultPacket(q);
        assertEquals(1, w.getResultPackets().size());
        List<?> l = w.getDocuments(3, 12);
        assertNotNull(l);
        assertEquals(0, l.size());
        l = w.getDocuments(0, 12);
        assertNotNull(l);
        assertEquals(0, l.size());
        l = w.getDocuments(0, 0);
        assertNotNull(l);
        assertEquals(0, l.size());
    }

    private PacketWrapper createResult(CacheKey key,
                                       int offset, int hits,
                                       int total) {
        QueryResultPacket r = createQueryResultPacket(offset, hits, total);
        return new PacketWrapper(key, new BasicPacket[] {r});
    }

    private QueryResultPacket createQueryResultPacket(int offset, int hits,
                                                      int total) {
        QueryResultPacket r = QueryResultPacket.create();
        r.setDocstamp(1);
        r.setChannel(0);
        r.setTotalDocumentCount(total);
        r.setOffset(offset);
        for (int i = 0; i < hits && i < total; ++i) {
            r.addDocument(new DocumentInfo(DocsumDefinitionTestCase.createGlobalId(offset + i),
                    1000 - offset - i, 1, 1));
        }
        return r;
    }

}
