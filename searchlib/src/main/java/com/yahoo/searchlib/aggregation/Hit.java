// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.Identifiable;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

/**
 * This class represents a generic hit with a rank value. Actual hits are represented using subclasses of this class.
 *
 * @author havardpe
 */
public abstract class Hit extends Identifiable {

    public static final int classId = registerClass(0x4000 + 94, Hit.class); // shared with c++
    private Object context = null;
    private double rank = 0.0;

    /**
     * Constructs an empty result node.
     */
    public Hit() {
        // empty
    }

    /**
     * Create a new hit with the given rank
     *
     * @param rank generic rank value
     */
    public Hit(double rank) {
        this.rank = rank;
    }

    /**
     * Obtain the rank of this hit. This is a comparable rank to allow multilevel sorting on arbitrary rank type.
     *
     * @return generic rank value
     */
    public double getRank() {
        return rank;
    }

    /**
     * Returns the context object of this hit.
     *
     * @return The context object.
     */
    public Object getContext() {
        return context;
    }

    /**
     * Sets the context object of this hit. This is not serialized, and is merely a tag used by the QRS.
     *
     * @param context The context to set.
     * @return This, to allow chaining.
     */
    public Hit setContext(Object context) {
        this.context = context;
        return this;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putDouble(null, rank);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        rank = buf.getDouble(null);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + (int)rank;
    }

    @SuppressWarnings({ "RedundantIfStatement" })
    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        Hit rhs = (Hit)obj;
        if (Double.compare(rank, rhs.rank) != 0) {
            return false;
        }
        if (!equals(context, rhs.context)) {
            return false;
        }
        return true;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("rank", rank);
        visitor.visit("context", context);
    }

}
