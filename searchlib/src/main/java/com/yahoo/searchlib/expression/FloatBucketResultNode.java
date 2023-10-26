// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This result holds a float value.
 *
 * @author <a href="mailto:havardpe@yahoo-inc.com">Haavard Pettersen</a>
 * @author Simon Thoresen Hult
 */
public class FloatBucketResultNode extends BucketResultNode {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 102, FloatBucketResultNode.class, FloatBucketResultNode::new);

    // bucket start, inclusive
    private double from = 0;

    // bucket end, exclusive
    private double to = 0;

    @Override
    public boolean empty() {
        return to == from;
    }

    /**
     * Constructs an empty result node.
     */
    public FloatBucketResultNode() {
        // empty
    }

    /**
     * Create a bucket with the given limits
     *
     * @param from bucket start
     * @param to   bucket end
     */
    public FloatBucketResultNode(double from, double to) {
        this.from = from;
        this.to = to;
    }

    /**
     * Obtain the bucket start
     *
     * @return bucket start
     */
    public double getFrom() {
        return from;
    }

    /**
     * Obtain the bucket end
     *
     * @return bucket end
     */
    public double getTo() {
        return to;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        buf.putDouble(null, from);
        buf.putDouble(null, to);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        from = buf.getDouble(null);
        to = buf.getDouble(null);
    }

    @Override
    protected int onCmp(ResultNode rhs) {
        if (classId != rhs.getClassId()) {
            return (classId - rhs.getClassId());
        }
        FloatBucketResultNode b = (FloatBucketResultNode)rhs;
        double f1 = from;
        double f2 = b.from;
        if (f1 < f2) {
            return -1;
        } else if (f1 > f2) {
            return 1;
        } else {
            double t1 = to;
            double t2 = b.to;
            if (t1 < t2) {
                return -1;
            } else if (t1 > t2) {
                return 1;
            }
        }
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
