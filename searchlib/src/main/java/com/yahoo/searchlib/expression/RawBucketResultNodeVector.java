// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.Serializer;

import java.util.ArrayList;

/**
 * @author baldersheim
 */
public class RawBucketResultNodeVector extends ResultNodeVector {
    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 126, RawBucketResultNodeVector.class);
    private ArrayList<RawBucketResultNode> vector = new ArrayList<RawBucketResultNode>();

    @Override
    protected int onGetClassId() {
        return classId;
    }

    public RawBucketResultNodeVector() {
    }

    public RawBucketResultNodeVector add(RawBucketResultNode v) {
        vector.add(v);
        return this;
    }

    public ResultNodeVector add(ResultNode r) {
        return add((RawBucketResultNode)r);
    }

    @Override public int size() { return vector.size(); }

    public ArrayList<RawBucketResultNode> getVector() {
        return vector;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putInt(null, vector.size());
        for (RawBucketResultNode node : vector) {
            node.serialize(buf);
        }
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        int sz = buf.getInt(null);
        vector = new ArrayList<RawBucketResultNode>();
        for (int i = 0; i < sz; i++) {
            RawBucketResultNode node = new RawBucketResultNode();
            node.deserialize(buf);
            vector.add(node);
        }
    }

    @Override
    protected int onCmp(ResultNode rhs) {
        if (classId != rhs.getClassId()) {
            return (classId - rhs.getClassId());
        }
        RawBucketResultNodeVector b = (RawBucketResultNodeVector)rhs;
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
