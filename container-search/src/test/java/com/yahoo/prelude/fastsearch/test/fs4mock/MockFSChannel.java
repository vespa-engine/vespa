// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test.fs4mock;

import com.yahoo.document.GlobalId;
import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.BufferTooSmallException;
import com.yahoo.fs4.DocumentInfo;
import com.yahoo.fs4.EolPacket;
import com.yahoo.fs4.GetDocSumsPacket;
import com.yahoo.fs4.Packet;
import com.yahoo.fs4.PacketDecoder;
import com.yahoo.fs4.PingPacket;
import com.yahoo.fs4.PongPacket;
import com.yahoo.fs4.QueryPacket;
import com.yahoo.fs4.QueryResultPacket;
import com.yahoo.fs4.mplex.Backend;
import com.yahoo.fs4.mplex.FS4Channel;
import com.yahoo.prelude.fastsearch.test.DocsumDefinitionTestCase;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * A channel which returns hardcoded packets of the same type as fdispatch
 */
public class MockFSChannel extends FS4Channel {

    /** The number of active documents this should report in ping reponses */
    private final long activeDocuments;
    
    public MockFSChannel() { 
        this(0); 
    }

    public MockFSChannel(long activeDocuments) {
        this.activeDocuments = activeDocuments;
    }

    private BasicPacket lastReceived = null;

    private QueryPacket lastQueryPacket = null;

    /** Initial value of docstamp */
    private static int docstamp = 1088490666;

    private static boolean emptyDocsums = false;

    @Override
    public synchronized boolean sendPacket(BasicPacket packet) {
        try {
            if (packet instanceof Packet)
                packet.encode(ByteBuffer.allocate(65536), 0);
        } catch (BufferTooSmallException e) {
            throw new RuntimeException("Too small buffer to encode packet in mock backend.");
        }

        if (packet instanceof QueryPacket)
            lastQueryPacket = (QueryPacket) packet;

        lastReceived = packet;
        return true;
    }

    /** Change docstamp to invalidate cache */
    public static void resetDocstamp() {
        docstamp = 1088490666;
    }

    /** Flip sending (in)valid docsums */
    public static void setEmptyDocsums(boolean d) {
        emptyDocsums = d;
    }

    /** Returns the last query packet received or null if none */
    public QueryPacket getLastQueryPacket() {
        return lastQueryPacket;
    }

    public BasicPacket getLastReceived() {
        return lastReceived;
    }

    public BasicPacket[] receivePackets(long timeout, int packetCount) {
        List<BasicPacket> packets = new java.util.ArrayList<>();

        if (lastReceived instanceof QueryPacket) {
            lastQueryPacket = (QueryPacket) lastReceived;
            QueryResultPacket result = QueryResultPacket.create();

            result.setDocstamp(docstamp);
            result.setChannel(0);
            result.setTotalDocumentCount(2);
            result.setOffset(lastQueryPacket.getOffset());

            if (lastQueryPacket.getOffset() == 0
                    && lastQueryPacket.getLastOffset() >= 1) {
                result.addDocument(
                        new DocumentInfo(DocsumDefinitionTestCase.createGlobalId(123),
                                         2003, 234, 0));
            }
            if (lastQueryPacket.getOffset() <= 1
                    && lastQueryPacket.getLastOffset() >= 2) {
                result.addDocument(
                        new DocumentInfo(DocsumDefinitionTestCase.createGlobalId(456),
                                         1855, 234, 1));
            }
            packets.add(result);
        } 
        else if (lastReceived instanceof GetDocSumsPacket) {
            addDocsums(packets, lastQueryPacket);
        }
        else if (lastReceived instanceof PingPacket) {
            packets.add(new PongPacket(activeDocuments));
        }
        while (packetCount >= 0 && packets.size() > packetCount) {
            packets.remove(packets.size() - 1);
        }

        return packets.toArray(new BasicPacket[packets.size()]);
    }

    /** Adds the number of docsums requested in queryPacket.getHits() */
    private void addDocsums(List packets, QueryPacket queryPacket) {
        int numHits = queryPacket.getHits();

        if (lastReceived instanceof GetDocSumsPacket) {
            numHits = ((GetDocSumsPacket) lastReceived).getNumDocsums();
        }
        for (int i = 0; i < numHits; i++) {
            ByteBuffer buffer;

            if (emptyDocsums) {
                buffer = createEmptyDocsumPacketData();
            } else {
                int[] docids = {
                    123, 456, 789, 789, 789, 789, 789, 789, 789,
                    789, 789, 789 };

                buffer = createDocsumPacketData(docids[i], DocsumDefinitionTestCase.makeDocsum());
            }
            buffer.position(0);
            packets.add(PacketDecoder.decode(buffer));
        }
        packets.add(EolPacket.create());
    }

    private ByteBuffer createEmptyDocsumPacketData() {
        ByteBuffer buffer = ByteBuffer.allocate(16);

        buffer.limit(buffer.capacity());
        buffer.position(0);
        buffer.putInt(12); // length
        buffer.putInt(205); // a code for docsumpacket
        buffer.putInt(0); // channel
        buffer.putInt(0); // dummy location
        return buffer;
    }

    private ByteBuffer createDocsumPacketData(int docid, byte[] docsumData) {
        ByteBuffer buffer = ByteBuffer.allocate(docsumData.length + 4 + 8 + GlobalId.LENGTH);

        buffer.limit(buffer.capacity());
        buffer.position(0);
        buffer.putInt(docsumData.length + 8 + GlobalId.LENGTH);
        buffer.putInt(205); // Docsum packet code
        buffer.putInt(0);
        byte[] rawGid = DocsumDefinitionTestCase.createGlobalId(docid).getRawId();
        buffer.put(rawGid);
        buffer.put(docsumData);
        return buffer;
    }

    public void close() {}
}
