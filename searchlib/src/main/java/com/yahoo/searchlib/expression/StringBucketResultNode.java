// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
public class StringBucketResultNode extends BucketResultNode {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 103, StringBucketResultNode.class);

    // bucket start, inclusive
    private ResultNode from = StringResultNode.getNegativeInfinity();

    // bucket end, exclusive
    private ResultNode to = StringResultNode.getNegativeInfinity();

    @Override
    public boolean empty() {
        return to.equals(from);
    }

    /**
     * Constructs an empty result node.
     */
    public StringBucketResultNode() {
        // empty
    }

    /**
     * Create a bucket with the given limits
     *
     * @param from bucket start
     * @param to   bucket end
     */
    public StringBucketResultNode(ResultNode from, ResultNode to) {
        this.from = from;
        this.to = to;
    }

    /**
     * Create a bucket with the given limits
     *
     * @param from bucket start
     * @param to   bucket end
     */
    public StringBucketResultNode(String from, String to) {
        this(new StringResultNode(from), new StringResultNode(to));
    }

    /**
     * Obtain the bucket start
     *
     * @return bucket start
     */
    public String getFrom() {
        return from.getString();
    }

    /**
     * Obtain the bucket end
     *
     * @return bucket end
     */
    public String getTo() {
        return to.getString();
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
        StringBucketResultNode b = (StringBucketResultNode)rhs;
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
