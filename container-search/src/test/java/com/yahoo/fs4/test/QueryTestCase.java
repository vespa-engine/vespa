// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4.test;

import com.yahoo.fs4.BufferTooSmallException;
import com.yahoo.fs4.Packet;
import com.yahoo.fs4.QueryPacket;
import com.yahoo.prelude.Freshness;
import com.yahoo.prelude.query.*;
import com.yahoo.prelude.querytransform.QueryRewrite;
import com.yahoo.search.Query;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Tests encoding of query x packages
 *
 * @author bratseth
 */
public class QueryTestCase extends junit.framework.TestCase {

    public QueryTestCase(String name) {
        super(name);
    }

    public void testEncodePacket() throws BufferTooSmallException {
        Query query=new Query("/?query=chain&timeout=0");
        query.setWindow(2, 8);
        QueryPacket packet=QueryPacket.create(query);
        assertEquals(2,packet.getOffset());
        assertEquals(8,packet.getHits());

        byte[] encoded = packetToBytes(packet);
        byte[] correctBuffer=new byte[] {0,0,0,46,0,0,0,-38,0,0,0,0, // Header
                                         0,0,0,6, // Features
                                         2,
                                         8,
                                         0,0,0,1, // querytimeout
                                         0,0,0x40,0x01,  // qflags
                                         7,
                                         'd', 'e', 'f', 'a', 'u', 'l', 't',
                                         0,0,0,1,0,0,0,8,4,
                                         0,5,
                                         99,104,97,105,110};
        assertEqualArrays(correctBuffer,encoded);
    }

    public void testEncodeQueryPacketWithSomeAdditionalFeatures() throws BufferTooSmallException {
        Query query=new Query("/?query=chain&dataset=10&type=phrase&timeout=0");
        // Because the rank mapping now needs config and a searcher,
        // we do the sledgehammer dance:
        query.getRanking().setProfile("two");
        query.setWindow(2, 8);
        QueryPacket packet=QueryPacket.create(query);
        byte[] encoded = packetToBytes(packet);
        byte[] correctBuffer=new byte[] {0,0,0,42,0,0,0,-38,0,0,0,0, // Header
                                         0,0,0,6, // Features
                                         2,
                                         8,
                                         0,0,0,1, // querytimeout
                                         0,0,0x40,0x01,  // QFlags
                                         3,
                                         't','w','o', // Ranking
                                         0,0,0,1,0,0,0,8,4,
                                         0,5,
                                         99,104,97,105,110};
        assertEqualArrays(correctBuffer, encoded);
    }

    /** This test will tell you if you have screwed up the binary encoding, but it won't tell you how */
    public void testEncodeQueryPacketWithManyFeatures() throws BufferTooSmallException {
        Query query=new Query("/?query=chain" +
                              "&ranking.features.query(foo)=30.3&ranking.features.query(bar)=0" +
                              "&ranking.properties.property.p1=v1&ranking.properties.property.p2=v2" +
                              "&pos.ll=S22.4532;W123.9887&pos.radius=3&pos.attribute=place&ranking.freshness=37" +
                              "&model.searchPath=7/3");
        query.getRanking().setFreshness(new Freshness("123456"));
        query.getRanking().setSorting("+field1 -field2");
        query.getRanking().setProfile("two");
        Highlight highlight = new Highlight();
        highlight.addHighlightTerm("field1","term1");
        highlight.addHighlightTerm("field1","term2");
        query.getPresentation().setHighlight(highlight);

        query.prepare();

        QueryPacket packet=QueryPacket.create(query);
        byte[] encoded = packetToBytes(packet);
        // System.out.println(Arrays.toString(encoded));
        byte[] correctBuffer=new byte[] {
            0, 0, 1, 23, 0, 0, 0, -38, 0, 0, 0, 0, 0, 16, 0, -122, 0, 10, ignored, ignored, ignored, ignored, 0, 0, 0x40, 0x01, 3, 't', 'w', 'o', 0, 0, 0, 3, 0, 0, 0, 4, 'r', 'a', 'n', 'k', 0, 0, 0, 5, 0, 0, 0, 11, 'p', 'r', 'o', 'p', 'e', 'r', 't', 'y', 46, 'p', '2', 0, 0, 0, 2, 'v', '2', 0, 0, 0, 11, 'p', 'r', 'o', 'p', 'e', 'r', 't', 'y', 46, 'p', '1', 0, 0, 0, 2, 'v', '1', 0, 0, 0, 3, 'f', 'o', 'o', 0, 0, 0, 4, '3', '0', 46, '3', 0, 0, 0, 3, 'b', 'a', 'r', 0, 0, 0, 1, '0', 0, 0, 0, 9, 'v', 'e', 's', 'p', 'a', 46, 'n', 'o', 'w', 0, 0, 0, 6, '1', '2', '3', '4', '5', '6', 0, 0, 0, 14, 'h', 'i', 'g', 'h', 'l', 'i', 'g', 'h', 't', 't', 'e', 'r', 'm', 's', 0, 0, 0, 3, 0, 0, 0, 6, 'f', 'i', 'e', 'l', 'd', '1', 0, 0, 0, 1, '2', 0, 0, 0, 6, 'f', 'i', 'e', 'l', 'd', '1', 0, 0, 0, 5, 't', 'e', 'r', 'm', '1', 0, 0, 0, 6, 'f', 'i', 'e', 'l', 'd', '1', 0, 0, 0, 5, 't', 'e', 'r', 'm', '2', 0, 0, 0, 5, 'm', 'o', 'd', 'e', 'l', 0, 0, 0, 1, 0, 0, 0, 10, 's', 'e', 'a', 'r', 'c', 'h', 'p', 'a', 't', 'h', 0, 0, 0, 3, '7', 47, '3', 0, 0, 0, 15, 43, 'f', 'i', 'e', 'l', 'd', '1', 32, 45, 'f', 'i', 'e', 'l', 'd', '2', 0, 0, 0, 1, 0, 0, 0, 9, 68, 1, 0, 5, 'c', 'h', 'a', 'i', 'n'
        };
        assertEqualArrays(correctBuffer,encoded);
    }

    /** This test will tell you if you have screwed up the binary encoding, but it won't tell you how */
    public void testEncodeQueryPacketWithManyFeaturesFresnhessAsString() throws BufferTooSmallException {
        Query query=new Query("/?query=chain" +
                "&ranking.features.query(foo)=30.3&ranking.features.query(bar)=0" +
                "&ranking.properties.property.p1=v1&ranking.properties.property.p2=v2" +
                "&pos.ll=S22.4532;W123.9887&pos.radius=3&pos.attribute=place&ranking.freshness=37" +
                "&model.searchPath=7/3");
        query.getRanking().setFreshness("123456");
        query.getRanking().setSorting("+field1 -field2");
        query.getRanking().setProfile("two");
        Highlight highlight = new Highlight();
        highlight.addHighlightTerm("field1","term1");
        highlight.addHighlightTerm("field1","term2");
        query.getPresentation().setHighlight(highlight);

        query.prepare();

        QueryPacket packet=QueryPacket.create(query);
        byte[] encoded = packetToBytes(packet);
        // System.out.println(Arrays.toString(encoded));
        byte[] correctBuffer=new byte[] {
            0, 0, 1, 23, 0, 0, 0, -38, 0, 0, 0, 0, 0, 16, 0, -122, 0, 10, ignored, ignored, ignored, ignored, 0, 0, 0x40, 0x01, 3, 't', 'w', 'o', 0, 0, 0, 3, 0, 0, 0, 4, 'r', 'a', 'n', 'k', 0, 0, 0, 5, 0, 0, 0, 11, 'p', 'r', 'o', 'p', 'e', 'r', 't', 'y', 46, 'p', '2', 0, 0, 0, 2, 'v', '2', 0, 0, 0, 11, 'p', 'r', 'o', 'p', 'e', 'r', 't', 'y', 46, 'p', '1', 0, 0, 0, 2, 'v', '1', 0, 0, 0, 3, 'f', 'o', 'o', 0, 0, 0, 4, '3', '0', 46, '3', 0, 0, 0, 3, 'b', 'a', 'r', 0, 0, 0, 1, '0', 0, 0, 0, 9, 'v', 'e', 's', 'p', 'a', 46, 'n', 'o', 'w', 0, 0, 0, 6, '1', '2', '3', '4', '5', '6', 0, 0, 0, 14, 'h', 'i', 'g', 'h', 'l', 'i', 'g', 'h', 't', 't', 'e', 'r', 'm', 's', 0, 0, 0, 3, 0, 0, 0, 6, 'f', 'i', 'e', 'l', 'd', '1', 0, 0, 0, 1, '2', 0, 0, 0, 6, 'f', 'i', 'e', 'l', 'd', '1', 0, 0, 0, 5, 't', 'e', 'r', 'm', '1', 0, 0, 0, 6, 'f', 'i', 'e', 'l', 'd', '1', 0, 0, 0, 5, 't', 'e', 'r', 'm', '2', 0, 0, 0, 5, 'm', 'o', 'd', 'e', 'l', 0, 0, 0, 1, 0, 0, 0, 10, 's', 'e', 'a', 'r', 'c', 'h', 'p', 'a', 't', 'h', 0, 0, 0, 3, '7', 47, '3', 0, 0, 0, 15, 43, 'f', 'i', 'e', 'l', 'd', '1', 32, 45, 'f', 'i', 'e', 'l', 'd', '2', 0, 0, 0, 1, 0, 0, 0, 9, 68, 1, 0, 5, 'c', 'h', 'a', 'i', 'n'
        };
        assertEqualArrays(correctBuffer,encoded);
    }

    public void testEncodeQueryPacketWithLabelsConnectivityAndSignificance() throws BufferTooSmallException {
        Query query=new Query();
        AndItem and = new AndItem();
        WeightedSetItem taggable1 = new WeightedSetItem("field1");
        taggable1.setLabel("foo");
        WeightedSetItem taggable2 = new WeightedSetItem("field2");
        taggable1.setLabel("bar");
        and.addItem(taggable1);
        and.addItem(taggable2);
        WordItem word1 = new WordItem("word1", "field3");
        word1.setSignificance(0.37);
        WordItem word2 = new WordItem("word1", "field3");
        word2.setSignificance(0.81);
        word2.setConnectivity(word1, 0.15);
        and.addItem(word1);
        and.addItem(word2);

        query.getModel().getQueryTree().setRoot(and);

        query.prepare();

        QueryPacket packet=QueryPacket.create(query);
        byte[] encoded = packetToBytes(packet);
        byte[] correctBuffer=new byte[] {
                0, 0, 1, 16, 0, 0, 0, -38, 0, 0, 0, 0, 0, 16, 0, 6, 0, 10, ignored, ignored, ignored, ignored, 0, 0, 0x40, 0x01, 7, 'd', 'e', 'f', 'a', 'u', 'l', 't', 0, 0, 0, 1, 0, 0, 0, 4, 'r', 'a', 'n', 'k', 0, 0, 0, 5, 0, 0, 0, 18, 'v', 'e', 's', 'p', 'a', 46, 'l', 'a', 'b', 'e', 'l', 46, 'b', 'a', 'r', 46, 'i', 'd', 0, 0, 0, 1, '1', 0, 0, 0, 22, 'v', 'e', 's', 'p', 'a', 46, 't', 'e', 'r', 'm', 46, '4', 46, 'c', 'o', 'n', 'n', 'e', 'x', 'i', 't', 'y', 0, 0, 0, 1, '3', 0, 0, 0, 22, 'v', 'e', 's', 'p', 'a', 46, 't', 'e', 'r', 'm', 46, '4', 46, 'c', 'o', 'n', 'n', 'e', 'x', 'i', 't', 'y', 0, 0, 0, 4, '0', 46, '1', '5', 0, 0, 0, 25, 'v', 'e', 's', 'p', 'a', 46, 't', 'e', 'r', 'm', 46, '3', 46, 's', 'i', 'g', 'n', 'i', 'f', 'i', 'c', 'a', 'n', 'c', 'e', 0, 0, 0, 4, '0', 46, '3', '7', 0, 0, 0, 25, 'v', 'e', 's', 'p', 'a', 46, 't', 'e', 'r', 'm', 46, '4', 46, 's', 'i', 'g', 'n', 'i', 'f', 'i', 'c', 'a', 'n', 'c', 'e', 0, 0, 0, 4, '0', 46, '8', '1', 0, 0, 0, 5, 0, 0, 0, '4', 1, 4, 79, 1, 0, 6, 'f', 'i', 'e', 'l', 'd', '1', 79, 2, 0, 6, 'f', 'i', 'e', 'l', 'd', '2', 68, 3, 6, 'f', 'i', 'e', 'l', 'd', '3', 5, 'w', 'o', 'r', 'd', '1', 68, 4, 6, 'f', 'i', 'e', 'l', 'd', '3', 5, 'w', 'o', 'r', 'd', 49
        };
        assertEqualArrays(correctBuffer,encoded);
    }

    public void testEncodeSortSpec() throws BufferTooSmallException {
        Query query=new Query("/?query=chain&sortspec=%2Ba+-b&timeout=0");
        query.setWindow(2, 8);
        QueryPacket packet=QueryPacket.create(query);
        ByteBuffer buffer=ByteBuffer.allocate(500);
        buffer.limit(0);
        packet.encode(buffer, 0);
        byte[] encoded=new byte[buffer.position()];
        buffer.rewind();
        buffer.get(encoded);
        byte[] correctBuffer=new byte[] {0,0,0,55,0,0,0,-38,0,0,0,0, // Header
                                         0,0,0,-122, // Features
                                         2,   // offset
                                         8,   // maxhits
                                         0,0,0,1, // querytimeout
                                         0,0,0x40,0x01,  // qflags
                                         7,
                                         'd', 'e', 'f', 'a', 'u', 'l', 't',
                                         0,0,0,5,   // sortspec length
                                         43,97,32,45,98,  // sortspec
                                         0,0,0,1,   // num stackitems
                                         0,0,0,8,4,
                                         0,5,
                                         99,104,97,105,110};
        assertEqualArrays(correctBuffer,encoded);

        // Encode again to test grantEncodingBuffer
        buffer = packet.grantEncodingBuffer(0);
        encoded = new byte[buffer.limit()];
        buffer.get(encoded);
        assertEqualArrays(correctBuffer,encoded);
    }

    public void testPhraseEqualsPhraseWithPhraseSegment() throws BufferTooSmallException {
        Query query = new Query();
        PhraseItem p = new PhraseItem();
        PhraseSegmentItem ps = new PhraseSegmentItem("a b", false, false);
        ps.addItem(new WordItem("a"));
        ps.addItem(new WordItem("b"));
        p.addItem(ps);
        query.getModel().getQueryTree().setRoot(p);

        query.setTimeout(0);
        QueryPacket queryPacket = QueryPacket.create(query);

        ByteBuffer buffer1 = ByteBuffer.allocate(1024);

        queryPacket.encode(buffer1, 0);

        query = new Query();
        p = new PhraseItem();
        p.addItem(new WordItem("a"));
        p.addItem(new WordItem("b"));
        query.getModel().getQueryTree().setRoot(p);

        query.setTimeout(0);
        queryPacket = QueryPacket.create(query);
        assertNotNull(queryPacket);

        ByteBuffer buffer2 = ByteBuffer.allocate(1024);

        queryPacket.encode(buffer2, 0);

        byte[] encoded1 = new byte[buffer1.position()];
        buffer1.rewind();
        buffer1.get(encoded1);
        byte[] encoded2 = new byte[buffer2.position()];
        buffer2.rewind();
        buffer2.get(encoded2);
        assertEqualArrays(encoded2, encoded1);
    }

    public void testPatchInChannelId() throws BufferTooSmallException {
        Query query=new Query("/?query=chain&timeout=0");
        query.setWindow(2, 8);
        QueryPacket packet=QueryPacket.create(query);
        assertEquals(2,packet.getOffset());
        assertEquals(8, packet.getHits());

        ByteBuffer buffer = packet.grantEncodingBuffer(0x07070707);

        byte[] correctBuffer=new byte[] {0,0,0,46,0,0,0,-38,7,7,7,7, // Header
                                         0,0,0,6, // Features
                                         2,
                                         8,
                                         0,0,0,1, // querytimeout
                                         0,0,0x40,0x01,  // qflags
                                         7,
                                         'd', 'e', 'f', 'a', 'u', 'l', 't',
                                         0,0,0,1,0,0,0,8,4,
                                         0,5,
                                         99,104,97,105,110};

        byte[] encoded=new byte[buffer.limit()];
        buffer.get(encoded);

        assertEqualArrays(correctBuffer,encoded);

        packet.allocateAndEncode(0x07070707);
        buffer = packet.grantEncodingBuffer(0x09090909);
        correctBuffer=new byte[] {0,0,0,46,0,0,0,-38,9,9,9,9, // Header
                                  0,0,0,6, // Features
                                  2,
                                  8,
                                  0,0,0,1, // querytimeout
                                  0,0,0x40,0x01,  // qflags
                                  7,
                                  'd', 'e', 'f', 'a', 'u', 'l', 't',
                                  0,0,0,1,0,0,0,8,4,
                                  0,5,
                                  99,104,97,105,110};

        encoded=new byte[buffer.limit()];
        buffer.get(encoded);

        assertEqualArrays(correctBuffer,encoded);
    }

    public static byte[] packetToBytes(Packet packet) {
        try {
            ByteBuffer buffer=ByteBuffer.allocate(500);
            buffer.limit(0);
            packet.encode(buffer,0);
            byte[] encoded=new byte[buffer.position()];
            buffer.rewind();
            buffer.get(encoded);
            return encoded;
        }
        catch (BufferTooSmallException e) {
            throw new RuntimeException(e);
        }
    }

    public static void assertEqualArrays(byte[] correct, byte[] test) {
        assertEquals("Incorrect length,", correct.length, test.length);
        for (int i=0; i<correct.length; i++) {
            if (correct[i] == ignored) continue; // Special value used to ignore bytes we don't want to check
            assertEquals("Byte nr " + i, correct[i], test[i]);
        }
    }

    public static final byte ignored = -128;

}
