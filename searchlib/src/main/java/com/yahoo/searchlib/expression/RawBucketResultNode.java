// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * @author baldersheim
 */
public class RawBucketResultNode extends BucketResultNode {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 125, RawBucketResultNode.class, RawBucketResultNode::new);

    // bucket start, inclusive
    private ResultNode from = RawResultNode.getNegativeInfinity();

    // bucket end, exclusive
    private ResultNode to = RawResultNode.getNegativeInfinity();

    @Override
    public boolean empty() {
        return to.equals(from);
    }

    public RawBucketResultNode() {}

    /**
     * Create a bucket with the given limits
     *
     * @param from bucket start
     * @param to   bucket end
     */
    public RawBucketResultNode(ResultNode from, ResultNode to) {
        this.from = from;
        this.to = to;
    }

    /**
     * Obtain the bucket start
     *
     * @return bucket start
     */
    public byte[] getFrom() {
        return from.getRaw();
    }

    /**
     * Obtain the bucket end
     *
     * @return bucket end
     */
    public byte[] getTo() {
        return to.getRaw();
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        serializeOptional(buf, from);
        serializeOptional(buf, to);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        from = (ResultNode)deserializeOptional(buf);
        to = (ResultNode)deserializeOptional(buf);
    }

    @Override
    protected int onCmp(ResultNode rhs) {
        if (classId != rhs.getClassId()) {
            return (classId - rhs.getClassId());
        }
        RawBucketResultNode b = (RawBucketResultNode)rhs;
        int diff = from.compareTo(b.from);
        return (diff == 0) ? to.compareTo(b.to) : diff;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + from.hashCode() + to.hashCode();
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("from", from);
        visitor.visit("to", to);
    }
}
