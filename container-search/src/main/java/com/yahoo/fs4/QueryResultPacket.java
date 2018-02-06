// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * An "extended query result" packet. This is the query result packets used today,
 * they allow more flexible sets of parameters to be shipped with query results.
 * This packet can be decoded only.
 *
 * @author bratseth
 */
public class QueryResultPacket extends Packet {

    /** This may have code 202, 208, 214 or 217 of historical reasons */
    private int code;

    /** Whether mld stuff, whatever that is, is included in this result */
    private boolean mldFeature=false;

    /** A feature of no apparent utility */
    private boolean datasetFeature=false;

    /** Whether coverage information is included in this result */
    private boolean coverageNodes = false;
    private long  coverageDocs = 0;
    private long  activeDocs = 0;
    private long  soonActiveDocs = 0;
    private int   degradedReason = 0;
    private short nodesQueried = 0;
    private short nodesReplied = 0;

    /** Whether the result contains grouping results **/
    private boolean groupDataFeature = false;

    /** Whether the result contains properties **/
    private boolean propsFeature = false;

    private long totalDocumentCount;

    private Number maxRank;

    private int docstamp;

    private int dataset=-1;

    private byte[] groupData = null;

    private List<DocumentInfo> documents=new ArrayList<>(10);

    public FS4Properties[] propsArray;

    private int offset;

    private QueryResultPacket() { }

    public static QueryResultPacket create() {
        return new QueryResultPacket();
    }

    public void setDocstamp(int docstamp){ this.docstamp=docstamp; }

    public int getDocstamp() { return docstamp; }

    /** Returns whether this has the mysterious mld feature */
    public boolean getMldFeature() { return mldFeature; }

    /** Returns whether this result has the dataset feature */
    public boolean getDatasetFeature() { return datasetFeature; }

    public boolean getCoverageFeature() { return true; }

    public long getCoverageDocs() { return coverageDocs; }

    public long getActiveDocs() { return activeDocs; }

    public long getSoonActiveDocs() { return soonActiveDocs; }

    public int getDegradedReason() { return degradedReason; }

    public boolean getCoverageFull() {
        return coverageDocs == activeDocs;
    }


    /** @return offset returned by backend */
    public int getOffset() { return offset; }

    /** Only for testing. */
    public void setOffset(int offset) {
        this.offset = offset;
    }

    @Override
    public void decodeBody(ByteBuffer buffer) {
        IntBuffer ints=buffer.asIntBuffer();

        decodeFeatures(ints);
        offset = ints.get();
        int documentCount=ints.get();
        buffer.position(buffer.position() + ints.position()*4);
        totalDocumentCount = buffer.getLong();
        maxRank = decodeMaxRank(buffer);
        ints = buffer.asIntBuffer();
        docstamp=ints.get();
        if (datasetFeature) dataset=ints.get();
        buffer.position(buffer.position() + ints.position()*4);
        if (groupDataFeature) {
            int len = buffer.getInt();
            groupData = new byte[len];
            buffer.get(groupData);
        }

        coverageDocs = buffer.getLong();
        activeDocs = buffer.getLong();
        soonActiveDocs = buffer.getLong();
        degradedReason = buffer.getInt();
        if (coverageNodes) {
            nodesQueried = buffer.getShort();
            nodesReplied = buffer.getShort();
        }

        decodeDocuments(buffer,documentCount);
        if (propsFeature) {
            int numMaps = buffer.getInt();
            propsArray = new FS4Properties[numMaps];
            for (int i = 0; i < numMaps; i++) {
                propsArray[i] = new FS4Properties();
                propsArray[i].decode(buffer);
            }
        }
    }

    private Number decodeMaxRank(ByteBuffer buffer) {
        return Double.valueOf(buffer.getDouble());
    }

    /**
     * feature bits
     */
    public static final int QRF_MLD             = 0x00000001;
    public static final int QRF_DATASETS        = 0x00000002;
    public static final int QRF_COVERAGE_NODES  = 0x00000004;
    public static final int QRF_SORTDATA        = 0x00000010;
    public static final int QRF_UNUSED_1        = 0x00000020;
    public static final int QRF_UNUSED_2        = 0x00000040;
    public static final int QRF_GROUPDATA       = 0x00000200;
    public static final int QRF_PROPERTIES      = 0x00000400;

    /**
     * Sets the features of this package.
     * Features are either encoded by different package codes
     * or by a feature int, for reasons not easily comprehended.
     */
    private void decodeFeatures(IntBuffer buffer) {
        switch (getCode()) {
        case 217:
                int features=buffer.get();
                mldFeature       = (QRF_MLD & features) != 0;
                datasetFeature   = (QRF_DATASETS & features) != 0;
                // Data given by sortFeature not currently used by QRS:
                // sortFeature   = (QRF_SORTDATA & features) != 0;
                coverageNodes    = (QRF_COVERAGE_NODES & features) != 0;
                groupDataFeature = (QRF_GROUPDATA & features) != 0;
                propsFeature     = (QRF_PROPERTIES & features) != 0;
                break;
            default:
                throw new RuntimeException("Programming error, packet " + getCode() + "Not expected.");
        }
    }

    private void decodeDocuments(ByteBuffer buffer, int documentCount) {
        for (int i=0; i<documentCount; i++) {
            documents.add(new DocumentInfo(buffer, this));
        }
    }

    public int getCode() { return code; }

    protected void codeDecodedHook(int code) { this.code=code; }

    public int getDocumentCount() { return documents.size(); }

    public String toString() {
        return "Query result x packet [" + getDocumentCount() + " documents]";
    }

    /** Returns the opaque grouping results **/
    public byte[] getGroupData() { return groupData; }


    /** Returns the total number of documents avalable for this query */
    public long getTotalDocumentCount() { return totalDocumentCount; }

    /** Only for testing. */
    public void setTotalDocumentCount(long totalDocumentCount) {
        this.totalDocumentCount = totalDocumentCount;
    }

    /** Returns a read-only list containing the DocumentInfo objects of this result */
    public List<DocumentInfo> getDocuments() {
        return Collections.unmodifiableList(documents);
    }

    public void addDocument(DocumentInfo document) {
        documents.add(document);
    }

    // TODO: Handle new maxRank intelligently
    public int getMaxRank() { return maxRank.intValue(); }

    public int getDataset() { return dataset; }

    public short getNodesQueried() { return nodesQueried; }
    public short getNodesReplied() { return nodesReplied; }

}
