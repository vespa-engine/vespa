// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.text.Utf8;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

public class VdsHit extends Hit {

    public static final int classId = registerClass(0x4000 + 96, VdsHit.class, VdsHit::new);
    private String docId = "";
    private RawData summary = new RawData();

    @SuppressWarnings("UnusedDeclaration")
    public VdsHit() {
        // user by deserializer
    }

    /**
     * Create a hit with the given path and document id.
     *
     * @param summary The summary blob standard fs4 coding.
     * @param docId   The local document id.
     * @param rank    The rank of this hit.
     */
    public VdsHit(String docId, byte[] summary, double rank) {
        super(rank);
        this.docId = docId;
        this.summary = new RawData(summary);
    }

    /**
     * Obtain the summary blob for this hit.
     *
     * @return The summary blob.
     */
    public RawData getSummary() {
        return summary;
    }

    /**
     * Obtain the local document id of this hit.
     *
     * @return The local document id.
     */
    public String getDocId() {
        return docId;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        byte[] utf8 = Utf8.toBytes(docId);
        buf.putInt(null, utf8.length);
        buf.put(null, utf8);
        summary.serialize(buf);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        docId = getUtf8(buf);
        summary.deserialize(buf);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + docId.hashCode() + summary.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        VdsHit rhs = (VdsHit)obj;
        return super.equals(obj) &&
               docId.equals(rhs.docId) &&
               summary.equals(rhs.summary);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("docId", docId);
        visitor.visit("summary", summary);
    }
}
