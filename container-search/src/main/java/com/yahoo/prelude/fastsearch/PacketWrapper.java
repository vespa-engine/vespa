// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.DocsumPacket;
import com.yahoo.fs4.DocumentInfo;
import com.yahoo.fs4.Packet;
import com.yahoo.fs4.QueryResultPacket;
import com.yahoo.document.GlobalId;
import com.yahoo.document.DocumentId;


/**
 * A wrapper for cache entries to make it possible to check whether the
 * hits are truly correct.
 *
 * @author Steinar Knutsen
 * @author Mathias Lidal
 */
// TODO: Remove packet cache as it timed out a long time ago.
// 1 - It does not work with grouping, 2 the packet protocol is eroding away.
public class PacketWrapper implements Cloneable {

    private static Logger log = Logger.getLogger(PacketWrapper.class.getName());

    final int keySize;
    // associated result packets, sorted in regard to offset
    private ArrayList<BasicPacket> resultPackets = new ArrayList<>(3); // length = "some small number"
    LinkedHashMap<DocsumPacketKey, BasicPacket> packets;

    private static class ResultPacketComparator<T extends BasicPacket> implements Comparator<T> {
        @Override
        public int compare(T o1, T o2) {
            QueryResultPacket r1 = (QueryResultPacket) o1;
            QueryResultPacket r2 = (QueryResultPacket) o2;
            return r1.getOffset() - r2.getOffset();
        }
    }

    private static ResultPacketComparator<BasicPacket> resultPacketComparator = new ResultPacketComparator<>();

    public PacketWrapper(CacheKey key, DocsumPacketKey[] packetKeys, BasicPacket[] bpackets) {
        // Should not support key == null
        this.keySize = key.byteSize();
        resultPackets.add(bpackets[0]);
        this.packets = new LinkedHashMap<>();
        Packet[] ppackets = new Packet[packetKeys.length];

        for (int i = 0; i < packetKeys.length; i++) {
            ppackets[i] = (Packet) bpackets[i + 1];
        }
        addDocsums(packetKeys, ppackets);
    }

    /**
     *  Only used by PacketCacheTestCase, should not be used otherwise
     */
    public PacketWrapper(CacheKey key, BasicPacket[] packets) {
        // Should support key == null as this is for testing
        if (key == null) {
            keySize = 0;
        } else {
            this.keySize = key.byteSize();
        }
        resultPackets.add(packets[0]);
        this.packets = new LinkedHashMap<>();
        for (int i = 0; i < packets.length - 1; i++) {
            this.packets.put(new DocsumPacketKey(new GlobalId(new DocumentId("doc:test:" + i).getGlobalId()), i, null), packets[i + 1]);
        }

    }

    public QueryResultPacket getFirstResultPacket() {
        if (resultPackets.size() > 0) {
            return (QueryResultPacket) resultPackets.get(0);
        } else {
            return null;
        }
    }

    /**
     * @return list of documents, null if not all are available
     */
    public List<DocumentInfo> getDocuments(int offset, int hits) {
        // speculatively allocate list for the hits
        List<DocumentInfo> docs = new ArrayList<>(hits);
        int currentOffset = 0;
        QueryResultPacket r = getFirstResultPacket();
        if (offset >= r.getTotalDocumentCount()) {
            // shortcut especially for results with 0 hits
            // >= both necessary for end of result sets and
            // offset == 0 && totalDocumentCount == 0
            return docs;
        }
        for (Iterator<BasicPacket> i = resultPackets.iterator(); i.hasNext();) {
            QueryResultPacket result = (QueryResultPacket) i.next();
            if (result.getOffset() > offset + currentOffset) {
                // we haven't got all the requested document info objects
                return null;
            }
            if (result.getOffset() + result.getDocumentCount()
                    <= currentOffset + offset) {
                // no new hits available
                continue;
            }
            List<DocumentInfo> documents = result.getDocuments();
            int packetOffset = (offset + currentOffset) - result.getOffset();
            int afterLastDoc = Math.min(documents.size(), packetOffset + hits);
            for (Iterator<DocumentInfo> j = documents.subList(packetOffset, afterLastDoc).iterator();
                    docs.size() < hits && j.hasNext();
                    ++currentOffset) {
                docs.add(j.next());
            }
            if (hits == docs.size()
                    || offset + docs.size() >= result.getTotalDocumentCount()) {
                // We have the hits we need, or there are no more hits available
                return docs;
            }
        }
        return null;
    }

    public void addResultPacket(QueryResultPacket resultPacket) {
        // This function only keeps the internal list sorted according
        // to offset
        int insertionPoint;
        QueryResultPacket r;

        if (resultPacket.getDocumentCount() == 0) {
            return; // do not add a packet which does not contain new info
        }

        insertionPoint = Collections.binarySearch(resultPackets, resultPacket, resultPacketComparator);
        if (insertionPoint < 0) {
            // new offset
            insertionPoint = ~insertionPoint; // (insertionPoint + 1) * -1;
            resultPackets.add(insertionPoint, resultPacket);
            cleanResultPackets();
        } else {
            // there exists a packet with same offset
            r = (QueryResultPacket) resultPackets.get(insertionPoint);
            if (resultPacket.getDocumentCount() > r.getDocumentCount()) {
                resultPackets.set(insertionPoint, resultPacket);
                cleanResultPackets();
            }
        }
    }

    private void cleanResultPackets() {
        int marker;
        QueryResultPacket previous;
        if (resultPackets.size() == 1) {
            return;
        }

        // we know the list is sorted with regard to offset
        // First ensure the list grows in regards to lastOffset as well.
        // Could have done this addResultPacket, but this makes the code
        // simpler.
        previous = (QueryResultPacket) resultPackets.get(0);
        for (int i = 1; i < resultPackets.size(); ++i) {
            QueryResultPacket r = (QueryResultPacket) resultPackets.get(i);
            if (r.getOffset() + r.getDocumentCount()
                    <= previous.getOffset() + previous.getDocumentCount()) {
                resultPackets.remove(i--);
            } else {
                previous = r;
            }
        }

        marker = 0;
        while (marker < (resultPackets.size() - 2)) {
            QueryResultPacket r0 = (QueryResultPacket) resultPackets.get(marker);
            QueryResultPacket r1 = (QueryResultPacket) resultPackets.get(marker + 1);
            QueryResultPacket r2 = (QueryResultPacket) resultPackets.get(marker + 2);
            int nextOffset = r0.getOffset() + r0.getDocumentCount();

            if (r1.getOffset() < nextOffset
                    && r2.getOffset() <= nextOffset) {
                resultPackets.remove(marker + 1);
            }
            ++marker;
        }
    }

    /** Only for testing. */
    public List<BasicPacket> getResultPackets() {
        return resultPackets;
    }

    public void addDocsums(DocsumPacketKey[] packetKeys, BasicPacket[] bpackets,
                           int offset) {
        Packet[] ppackets = new Packet[packetKeys.length];

        for (int i = 0; i < packetKeys.length; i++) {
            ppackets[i] = (Packet) bpackets[i + offset];
        }
        addDocsums(packetKeys, ppackets);
    }

    public void addDocsums(DocsumPacketKey[] packetKeys, Packet[] packets) {
        if (packetKeys == null || packets == null) {
            log.warning(
                    "addDocsums called with "
                            + (packetKeys == null ? "packetKeys == null " : "")
                            + (packets == null ? "packets == null" : ""));
            return;
        }
        for (int i = 0; i < packetKeys.length && i < packets.length; i++) {
            if (packetKeys[i] == null) {
                log.warning(
                        "addDocsums called, but packetsKeys[" + i + "] is null");
            } else if (packets[i] instanceof DocsumPacket) {
                DocsumPacket dp = (DocsumPacket) packets[i];

                if (packetKeys[i].getGlobalId().equals(dp.getGlobalId())
                    && dp.getData().length > 0)
                {
                    this.packets.put(packetKeys[i], packets[i]);
                    log.fine("addDocsums " + i + " globalId: " + dp.getGlobalId());
                } else {
                    log.warning("not caching bad Docsum for globalId " + packetKeys[i].getGlobalId() + ": " + dp);
                }
            } else {
                log.warning(
                        "addDocsums called, but packets[" + i
                        + "] is not a DocsumPacket instance");
            }
        }
    }

    public int getNumPackets() {
        return packets.size();
    }

    BasicPacket getPacket(GlobalId globalId, int partid, String summaryClass) {
        return getPacket(
                new DocsumPacketKey(globalId, partid, summaryClass));
    }

    BasicPacket getPacket(DocsumPacketKey packetKey) {
        return packets.get(packetKey);
    }

    long getTimestamp() {
        return getFirstResultPacket().getTimestamp();
    }

    public void setTimestamp(long timestamp) {
        getFirstResultPacket().setTimestamp(timestamp);
    }

    public int getPacketsSize() {
        int size = 0;

        for (Iterator<BasicPacket> i = resultPackets.iterator(); i.hasNext();) {
            QueryResultPacket r = (QueryResultPacket) i.next();
            int l = r.getLength();

            if (l < 0) {
                log.warning("resultpacket length " + l);
                l = 10240;
            }
            size += l;
        }
        for (Iterator<BasicPacket> i = packets.values().iterator(); i.hasNext();) {
            BasicPacket packet = i.next();
            int l = packet.getLength();

            if (l < 0) {
                log.warning("BasicPacket length " + l);
                l = 10240;
            }
            size += l;
        }
        size += keySize;
        return size;
    }

    /**
     * Straightforward shallow copy.
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object clone() {
        try {
            PacketWrapper other = (PacketWrapper) super.clone();
            other.resultPackets = (ArrayList<BasicPacket>) resultPackets.clone();
            if (packets != null) {
                other.packets = (LinkedHashMap<DocsumPacketKey, BasicPacket>) packets.clone();
            }
            return other;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("A non-cloneable superclass has been inserted.",
                    e);
        }
    }

}
