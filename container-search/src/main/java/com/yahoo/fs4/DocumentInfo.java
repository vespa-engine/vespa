// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import com.yahoo.document.GlobalId;

import java.nio.ByteBuffer;

/**
 * Meta attributes on documents (not the document summaries themselves).
 * Used in query results and get docusum packages
 *
 * @author bratseth
 */
public class DocumentInfo implements Cloneable {

    private final GlobalId globalId;
    private final double metric;
    private final int partId;
    private final int distributionKey;
    private final byte[] sortData;

    DocumentInfo(ByteBuffer buffer, QueryResultPacket owner) {
        this(buffer, owner, null);
    }

    DocumentInfo(ByteBuffer buffer, QueryResultPacket owner, byte[] sortData) {
        byte[] rawGid = new byte[GlobalId.LENGTH];
        buffer.get(rawGid);
        globalId = new GlobalId(rawGid);
        metric = decodeMetric(buffer);
        partId = owner.getMldFeature() ? buffer.getInt() : 0;
        distributionKey = owner.getMldFeature() ? buffer.getInt() : 0;
        this.sortData = sortData;
    }

    public DocumentInfo(GlobalId globalId, int metric, int partId, int distributionKey) {
        this.globalId = globalId;
        this.metric = metric;
        this.partId = partId;
        this.distributionKey = distributionKey;
        this.sortData = null;
    }

    private double decodeMetric(ByteBuffer buffer) {
        return buffer.getDouble();
    }

    public GlobalId getGlobalId() { return globalId; }

    /** Raw rank score */
    public double getMetric() { return metric; }

    /** Partition this document resides on */
    public int getPartId() { return partId; }

    /** Unique key for the node this document resides on */
    public int getDistributionKey() { return distributionKey; }

    public byte[] getSortData() {
        return sortData;
    }

    public String toString() {
        return "document info [globalId=" + globalId + ", metric=" + metric + "]";
    }

   /**
     * Implements the Cloneable interface
     */
    public Object clone() {
        try {
            DocumentInfo docInfo=(DocumentInfo) super.clone();
            return docInfo;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Someone inserted a nonclonable superclass");
        }
    }
}
