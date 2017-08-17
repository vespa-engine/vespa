// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import com.yahoo.document.GlobalId;
import com.yahoo.log.LogLevel;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.query.Item;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.text.Utf8;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * <p>A packet for requesting a list of document summaries.
 * This packet can be encoded only.</p>
 *
 * @author bratseth
 */
public class GetDocSumsPacket extends Packet {

    /** Session id key. Yep, putting this here is ugly as hell */
    public static final String sessionIdKey = "sessionId";

    private static final Logger log = Logger.getLogger(GetDocSumsPacket.class.getName());
    private final Result result;
    private final Query query;
    private final String summaryClass;
    private QueryPacketData queryPacketData = null;
    private int flags = 0;

    /**
     * True if we should send the query with this docsum, false otherwise.
     * Sending the query is necessary if we need to return summary features or generate a dynamic summary
     */
    private final boolean sendQuery;

    private GetDocSumsPacket(Result result, String summaryClass, boolean sendQuery) {
        this.result = result;
        this.query = result.getQuery();
        this.summaryClass = summaryClass;
        this.sendQuery = sendQuery;
    }

    /**
     * Creates a get docsums packet for a certain result
     */
    public static GetDocSumsPacket create(Result result, String summaryClass, boolean sendQuery) {
        return new GetDocSumsPacket(result, summaryClass, sendQuery);
    }

    /**
     * features bits, as given in searchlib/src/searchlib/common/packets.h
     * definition of enum getdocsums_features
     */
    public static final int GDF_MLD = 0x00000001;
    public static final int GDF_QUERYSTACK = 0x00000004;
    public static final int GDF_RANKP_QFLAGS = 0x00000010;
    public static final int GDF_LOCATION = 0x00000080;
    public static final int GDF_RESCLASSNAME = 0x00000800;
    public static final int GDF_PROPERTIES = 0x00001000;
    public static final int GDF_FLAGS = 0x00002000;

    /**
     * flag bits, as given in fastserver4/src/network/transport.h
     * definition of enum getdocsums_flags
     */
    public static final int GDFLAG_IGNORE_ROW  = 0x00000001;
    public static final int GDFLAG_ALLOW_SLIME = 0x00000002;

    public void encodeBody(ByteBuffer buffer) {
        setFieldsFromHits();

        boolean useQueryCache = query.getRanking().getQueryCache();
        // If feature cache is used we need to include the sessionId as key.
        if (useQueryCache) { // TODO: Move this decision (and the key) to ranking
            query.getRanking().getProperties().put(sessionIdKey, query.getSessionId(false).toString());
        }

        // always allow slime docsums
        flags |= GDFLAG_ALLOW_SLIME;

        // set the default features
        long features = GDF_MLD;
        if (sendQuery)
            features |= GDF_QUERYSTACK;
        features |= GDF_RANKP_QFLAGS;

        // do we want a specific result class?
        if (summaryClass != null)
            features |= GDF_RESCLASSNAME;
        if (query.getRanking().getLocation() != null)
            features |= GDF_LOCATION;
        if (query.hasEncodableProperties())
            features |= GDF_PROPERTIES;
        if (flags != 0) {
            features |= GDF_FLAGS;
        }
        buffer.putInt((int)features);
        buffer.putInt(0);     //Unused, was docstamp
        long timeLeft = query.getTimeLeft();
        final int minTimeout = 50;
        buffer.putInt(Math.max(minTimeout, (int)timeLeft));
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "Timeout from query(" + query.getTimeout() + "), sent to backend: " + Math.max(minTimeout, timeLeft));
        }

        if (queryPacketData != null)
            encodeQueryFromPacketData(buffer, useQueryCache);
        else
            encodeQuery(buffer);

        if (flags != 0)
            buffer.putInt(flags);
        encodeDocIds(buffer);
    }

    private void setFieldsFromHits() {
        for (Iterator<Hit> i = result.hits().unorderedDeepIterator(); i.hasNext(); ) {
            Hit h = i.next();
            if (h instanceof FastHit) {
                FastHit hit = (FastHit)h;
                if (hit.shouldIgnoreRowBits()) {
                    flags |= GDFLAG_IGNORE_ROW;
                }
                QueryPacketData tag = hit.getQueryPacketData();
                if (tag != null) {
                    this.queryPacketData = tag;
                    break;
                }
            }
        }
    }

    private void encodeQueryFromPacketData(ByteBuffer buffer, boolean reencodePropertyMaps) {
        queryPacketData.encodeRankProfile(buffer);
        queryPacketData.encodeQueryFlags(buffer);

        encodeSummaryClass(buffer);

        if (reencodePropertyMaps || ! sendQuery) // re-encode when we're not sending query, to avoid resending all encoded properties
            query.encodeAsProperties(buffer, sendQuery);
        else
            queryPacketData.encodePropertyMaps(buffer);

        if (sendQuery)
            queryPacketData.encodeQueryStack(buffer);
        queryPacketData.encodeLocation(buffer);
    }

    private void encodeSummaryClass(ByteBuffer buffer) {
        if (summaryClass != null) {
            byte[] tmp = Utf8.toBytes(summaryClass);
            buffer.putInt(tmp.length);
            buffer.put(tmp);
        }
    }

    private void encodeQuery(ByteBuffer buffer) {
        Item.putString(query.getRanking().getProfile(), buffer);
        buffer.putInt(QueryPacket.getQueryFlags(query));

        encodeSummaryClass(buffer);

        query.encodeAsProperties(buffer, sendQuery);

        if (sendQuery) {
            // The stack must be resubmitted to generate dynamic docsums
            int itemCountPosition = buffer.position();
            buffer.putInt(0);
            int dumpLengthPosition = buffer.position();
            buffer.putInt(0);
            int count = query.encode(buffer);
            buffer.putInt(itemCountPosition, count);
            buffer.putInt(dumpLengthPosition, buffer.position() - dumpLengthPosition - 4);
        }

        if (query.getRanking().getLocation() != null) {
            int locationLengthPosition = buffer.position();
            buffer.putInt(0);
            int locationLength = query.getRanking().getLocation().encode(buffer);
            buffer.putInt(locationLengthPosition, locationLength);
        }
    }

    private void encodeDocIds(ByteBuffer buffer) {
        byte[] emptyGid = new byte[GlobalId.LENGTH];
        for (Iterator<Hit> i = result.hits().unorderedDeepIterator(); i.hasNext(); ) {
            Hit hit = i.next();
            if (hit instanceof FastHit && !hit.isFilled(summaryClass)) {
                FastHit fastHit = (FastHit)hit;
                buffer.put(fastHit.getGlobalId() != null ? fastHit.getGlobalId().getRawId() : emptyGid);
                buffer.putInt(fastHit.getPartId());
                buffer.putInt(0);  //Unused, was docstamp
            }
        }
    }

    public int getCode() {
        return 219;
    }

    public String toString() {
        return "Get docsums x packet fetching " + getNumDocsums() + " docsums and packet length of " + getLength() + " bytes.";
    }

    public int getNumDocsums() {
        int num = 0;
        for (Iterator<Hit> i = result.hits().unorderedDeepIterator(); i.hasNext(); ) {
            Hit hit = i.next();
            if (hit instanceof FastHit && !hit.isFilled(summaryClass)) {
                num++;
            }
        }
        return num;
    }

    /**
     * Return the document summary class we want the fdispatch
     * to use when replying to us
     */
    @SuppressWarnings("UnusedDeclaration")
    public String getSummaryClass() {
        return summaryClass;
    }
}
