// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4.test;

import com.yahoo.document.GlobalId;
import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.DocumentInfo;
import com.yahoo.fs4.PacketDecoder;
import com.yahoo.fs4.QueryResultPacket;

import java.nio.ByteBuffer;

/**
 * Tests encoding of query packages
 *
 * @author bratseth
 */
public class QueryResultTestCase extends junit.framework.TestCase {

    public QueryResultTestCase(String name) {
        super(name);
    }

    private static GlobalId gid1 = new GlobalId(new byte[] {1,1,1,1,1,1,1,1,1,1,1,1});
    private static GlobalId gid2 = new GlobalId(new byte[] {2,2,2,2,2,2,2,2,2,2,2,2});

    public void testDecodeQueryResultX() {
         byte[] packetData=new byte[] {
                 0,0,0,100,
                 0,0,0,217-256,
                 0,0,0,1,
                 0,0,0,1,
                 0,0,0,0,
                 0,0,0,2,
                 0,0,0,0,0,0,0,5,
                 0x40,0x39,0,0,0,0,0,0,
                 0,0,0,111,
                 0,0,0,0,0,0,0,89,
                 0,0,0,0,0,0,0,90,
                 0,0,0,0,0,0,0,91,
                 0,0,0,1,
                 1,1,1,1,1,1,1,1,1,1,1,1, 0x40,0x37,0,0,0,0,0,0, 0,0,0,7, 0,0,0,36,
                 2,2,2,2,2,2,2,2,2,2,2,2, 0x40,0x35,0,0,0,0,0,0, 0,0,0,8, 0,0,0,37
         };
        ByteBuffer buffer=ByteBuffer.allocate(200);
        buffer.put(packetData);
        buffer.flip();
        BasicPacket packet=PacketDecoder.decode(buffer);
        assertTrue(packet instanceof QueryResultPacket);
        QueryResultPacket result=(QueryResultPacket)packet;

        assertTrue(result.getMldFeature());

        assertEquals(5,result.getTotalDocumentCount());
        assertEquals(25,result.getMaxRank());
        assertEquals(111,result.getDocstamp());
        assertEquals(89, result.getCoverageDocs());
        assertEquals(90, result.getActiveDocs());
        assertEquals(91, result.getSoonActiveDocs());
        assertEquals(1, result.getDegradedReason());

        assertEquals(2,result.getDocuments().size());
        DocumentInfo document1= result.getDocuments().get(0);
        assertEquals(gid1,document1.getGlobalId());
        assertEquals(23.0,document1.getMetric());
        assertEquals(7,document1.getPartId());
        assertEquals(36,document1.getDistributionKey());
        DocumentInfo document2= result.getDocuments().get(1);
        assertEquals(gid2,document2.getGlobalId());
        assertEquals(21.0,document2.getMetric());
        assertEquals(8,document2.getPartId());
        assertEquals(37,document2.getDistributionKey());
    }

    public void testDecodeQueryResultMoreHits() {
         byte[] packetData=new byte[] {
                 0,0,0,100,
                 0,0,0,217-256,
                 0,0,0,1,
                 0,0,0,3,
                 0,0,0,0,
                 0,0,0,2,
                 0,0,0,0,0,0,0,5,
                 0x40,0x39,0,0,0,0,0,0,
                 0,0,0,111,
                 0,6,0,5,
                 0,0,0,0,0,0,0,89,
                 0,0,0,0,0,0,0,90,
                 0,0,0,0,0,0,0,91,
                 0,0,0,1,
                 1,1,1,1,1,1,1,1,1,1,1,1, 0x40,0x37,0,0,0,0,0,0, 0,0,0,7, 0,0,0,36,
                 2,2,2,2,2,2,2,2,2,2,2,2, 0x40,0x35,0,0,0,0,0,0, 0,0,0,8, 0,0,0,37
         };
        ByteBuffer buffer=ByteBuffer.allocate(200);
        buffer.put(packetData);
        buffer.flip();
        BasicPacket packet=PacketDecoder.decode(buffer);
        assertTrue(packet instanceof QueryResultPacket);
        QueryResultPacket result=(QueryResultPacket)packet;

        assertEquals(2,result.getDocuments().size());
        DocumentInfo document1= result.getDocuments().get(0);
        assertEquals(gid1,document1.getGlobalId());
        DocumentInfo document2= result.getDocuments().get(1);
        assertEquals(gid2,document2.getGlobalId());
        assertEquals(6, result.getNodesQueried());
        assertEquals(5, result.getNodesReplied());
    }
}
