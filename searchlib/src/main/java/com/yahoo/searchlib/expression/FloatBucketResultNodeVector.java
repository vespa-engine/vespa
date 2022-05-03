// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.Serializer;

import java.util.ArrayList;

/**
 * This result holds nothing.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class FloatBucketResultNodeVector extends ResultNodeVector {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 113, FloatBucketResultNodeVector.class);
    private ArrayList<FloatBucketResultNode> vector = new ArrayList<FloatBucketResultNode>();

    @Override
    protected int onGetClassId() {
        return classId;
    }

    public FloatBucketResultNodeVector() {
    }

    public FloatBucketResultNodeVector add(FloatBucketResultNode v) {
        vector.add(v);
        return this;
    }

    public ResultNodeVector add(ResultNode r) {
        return add((FloatBucketResultNode)r);
    }

    @Override public int size() { return vector.size(); }

    public ArrayList<FloatBucketResultNode> getVector() {
        return vector;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putInt(null, vector.size());
        for (FloatBucketResultNode node : vector) {
            node.serialize(buf);
        }
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        int sz = buf.getInt(null);
        vector = new ArrayList<FloatBucketResultNode>();
        for (int i = 0; i < sz; i++) {
            FloatBucketResultNode node = new FloatBucketResultNode(0, 0);
            node.deserialize(buf);
            vector.add(node);
        }
    }

    @Override
    protected int onCmp(ResultNode rhs) {
        if (classId != rhs.getClassId()) {
            return (classId - rhs.getClassId());
        }
        FloatBucketResultNodeVector b = (FloatBucketResultNodeVector)rhs;
        int minLength = vector.size();
        if (b.vector.size() < minLength) {
            minLength = b.vector.size();
        }
        int diff = 0;
        for (int i = 0; (diff == 0) && (i < minLength); i++) {
            diff = vector.get(i).compareTo(b.vector.get(i));
        }
        return (diff == 0) ? (vector.size() - b.vector.size()) : diff;
    }

}
