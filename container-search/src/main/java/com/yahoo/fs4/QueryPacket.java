// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import com.yahoo.compress.IntegerCompressor;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.prelude.query.Item;
import com.yahoo.search.Query;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.grouping.vespa.GroupingExecutor;
import com.yahoo.search.query.Ranking;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.text.Utf8String;
import com.yahoo.vespa.objects.BufferSerializer;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * An "extended query" packet. This is the query packets used today,
 * they allow more flexible sets of parameters to be shipped with queries.
 * This packet can be encoded only.
 *
 * @author bratseth
 * @author Bjørn Borud
 */
public class QueryPacket extends Packet {

    private final String serverId;
    private final Query query;

    private QueryPacketData queryPacketData;

    private QueryPacket(String serverId, Query query) {
        this.serverId = serverId;
        this.query = query;
    }

    /** Returns the query from which this packet is populated */
    public Query getQuery() {
        return query;
    }

    /**
     * Creates and returns a query packet
     *
     * @param query the query to convert to a packet
     */
    public static QueryPacket create(String serverId, Query query) {
        return new QueryPacket(serverId, query);
    }


    /** Returns the first offset requested */
    public int getOffset() {
        return query.getOffset();
    }

    /**
     * Returns the <i>last</i> offset requested (inclusively), that is
     * getOffset() + getHits()
     */
    public int getLastOffset() {
        return getOffset() + getHits();
    }

    /** Returns the number of hits requested */
    public int getHits() {
        return query.getHits();
    }

    public void encodeBody(ByteBuffer buffer) {
        queryPacketData = new QueryPacketData();

        boolean sendSessionKey = query.getGroupingSessionCache() || query.getRanking().getQueryCache();
        int featureFlag = getFeatureInt(sendSessionKey);
        buffer.putInt(featureFlag);

        IntegerCompressor.putCompressedPositiveNumber(getOffset(), buffer);
        IntegerCompressor.putCompressedPositiveNumber(getHits(), buffer);
        buffer.putInt(Math.max(1, (int)query.getTimeLeft())); // Safety to avoid sending down 0 or negative number
        buffer.putInt(getFlagInt());
        int startOfFieldToSave = buffer.position();
        Item.putString(query.getRanking().getProfile(), buffer);
        queryPacketData.setRankProfile(buffer, startOfFieldToSave);

        if ( (featureFlag & QF_PROPERTIES) != 0) {
            startOfFieldToSave = buffer.position();
            query.encodeAsProperties(buffer, true);
            queryPacketData.setPropertyMaps(buffer, startOfFieldToSave);
        }

        // Language not needed when sending query stacks

        if ((featureFlag & QF_SORTSPEC) != 0) {
            int sortSpecLengthPosition=buffer.position();
            buffer.putInt(0);
            int sortSpecLength = query.getRanking().getSorting().encode(buffer);
            buffer.putInt(sortSpecLengthPosition, sortSpecLength);
        }

        if ( (featureFlag & QF_GROUPSPEC) != 0) {
            List<Grouping> groupingList = GroupingExecutor.getGroupingList(query);
            BufferSerializer gbuf = new BufferSerializer(new GrowableByteBuffer());
            gbuf.putInt(null, groupingList.size());
            for (Grouping g: groupingList){
                g.serialize(gbuf);
            }
            gbuf.getBuf().flip();
            byte[] blob = new byte [gbuf.getBuf().limit()];
            gbuf.getBuf().get(blob);
            buffer.putInt(blob.length);
            buffer.put(blob);
        }

        if (sendSessionKey) {
            Utf8String key = query.getSessionId(serverId).asUtf8String();
            buffer.putInt(key.getByteLength());
            buffer.put(key.getBytes());
        }

        if ((featureFlag & QF_LOCATION) != 0) {
            startOfFieldToSave = buffer.position();
            int locationLengthPosition=buffer.position();
            buffer.putInt(0);
            int locationLength= query.getRanking().getLocation().encode(buffer);
            buffer.putInt(locationLengthPosition, locationLength);
            queryPacketData.setLocation(buffer, startOfFieldToSave);
        }

        startOfFieldToSave = buffer.position();
        int stackItemPosition=buffer.position();
        buffer.putInt(0); // Number of stack items written below
        int stackLengthPosition = buffer.position();
        buffer.putInt(0);
        int stackPosition = buffer.position();
        int stackItemCount=query.encode(buffer);
        int stackLength = buffer.position() - stackPosition;
        buffer.putInt(stackItemPosition,stackItemCount);
        buffer.putInt(stackLengthPosition, stackLength);
        queryPacketData.setQueryStack(buffer, startOfFieldToSave);
    }

    /**
     * feature bits, taken from searchlib/common/transport.h
     */
    private static final int QF_PARSEDQUERY     = 0x00000002;
    private static final int QF_RANKP           = 0x00000004;
    private static final int QF_SORTSPEC        = 0x00000080;
    private static final int QF_LOCATION        = 0x00000800;
    private static final int QF_PROPERTIES      = 0x00100000;
    private static final int QF_GROUPSPEC       = 0x00400000;
    private static final int QF_SESSIONID       = 0x00800000;

    private int getFeatureInt(boolean sendSessionId) {
        int features = QF_PARSEDQUERY | QF_RANKP; // this bitmask means "parsed query" in query packet.
                                                  // And rank properties. Both are always present

        features |= (query.getRanking().getSorting() != null)   ? QF_SORTSPEC   : 0;
        features |= (query.getRanking().getLocation() != null)  ? QF_LOCATION   : 0;
        features |= (query.hasEncodableProperties())            ? QF_PROPERTIES : 0;
        features |= GroupingExecutor.hasGroupingList(query)     ? QF_GROUPSPEC  : 0;
        features |= (sendSessionId)                             ? QF_SESSIONID  : 0;

        return features;
    }

    /**
     * query flag bits, taken from searchlib/common/transport.h
     */
    private static final int QFLAG_EXTENDED_COVERAGE    = 0x00000001;
    private static final int QFLAG_COVERAGE_NODES       = 0x00000002;
    private static final int QFLAG_ESTIMATE             = 0x00000080;
    private static final int QFLAG_DROP_SORTDATA        = 0x00004000;
    private static final int QFLAG_NO_RESULTCACHE       = 0x00010000;
    private static final int QFLAG_DUMP_FEATURES        = 0x00040000;

    private int getFlagInt() {
        int flags = getQueryFlags(query);
        queryPacketData.setQueryFlags(flags);
        flags |= query.properties().getBoolean(Dispatcher.dispatchInternal, false) ? 0 : QFLAG_DROP_SORTDATA;
        return flags;
    }


    public int getCode() {
        return 218;
    }

    public String toString() {
        return "Query x packet [query: " + query + "]";
    }

    static int getQueryFlags(Query query) {
        int flags = QFLAG_EXTENDED_COVERAGE | QFLAG_COVERAGE_NODES;

        flags |= query.properties().getBoolean(com.yahoo.search.query.Model.ESTIMATE) ? QFLAG_ESTIMATE : 0;
        flags |= query.getNoCache() ? QFLAG_NO_RESULTCACHE : 0;
        flags |= query.properties().getBoolean(Ranking.RANKFEATURES, false) ? QFLAG_DUMP_FEATURES : 0;
        return flags;
    }

    /**
     * Fetch a binary wrapper containing data from encoding process for use in
     * creating a summary request.
     *
     * @return wrapper object suitable for creating a summary fetch packet
     * @throws IllegalStateException if no wrapper has been generated
     */
    public QueryPacketData getQueryPacketData() {
        if (queryPacketData == null)
            throw new IllegalStateException("Trying to fetch a hit tag without having encoded the packet first.");
        return queryPacketData;
    }

}
