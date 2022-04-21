// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This is an integer bucket value
 *
 * @author <a href="mailto:havardpe@yahoo-inc.com">Haavard Pettersen</a>
 * @author Simon Thoresen Hult
 */
public class IntegerBucketResultNode extends BucketResultNode {

    public static final int classId = registerClass(0x4000 + 101, IntegerBucketResultNode.class);
    private long from = 0; // bucket start, inclusive
    private long to = 0; // bucket end, exclusive

    /**
     * Constructs an empty result node.
     */
    public IntegerBucketResultNode() {
        // empty
    }

    /**
     * Create a bucket with the given limits
     *
     * @param from bucket start
     * @param to   bucket end
     */
    public IntegerBucketResultNode(long from, long to) {
        this.from = from;
        this.to = to;
    }

    /**
     * Obtain the bucket start
     *
     * @return bucket start
     */
    public long getFrom() {
        return from;
    }

    /**
     * Obtain the bucket end
     *
     * @return bucket end
     */
    public long getTo() {
        return to;
    }

    @Override
    public boolean empty() {
        return to == from;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        buf.putLong(null, from);
        buf.putLong(null, to);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        from = buf.getLong(null);
        to = buf.getLong(null);
    }

    @Override
    protected int onCmp(ResultNode rhs) {
        if (classId != rhs.getClassId()) {
            return (classId - rhs.getClassId());
        }
        IntegerBucketResultNode b = (IntegerBucketResultNode)rhs;
        if (from < b.from) return -1;
        if (from > b.from) return 1;
        if (to < b.to) return -1;
        if (to > b.to) return 1;
        return 0;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + (int)from + (int)to;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("from", from);
        visitor.visit("to", to);
    }
}
