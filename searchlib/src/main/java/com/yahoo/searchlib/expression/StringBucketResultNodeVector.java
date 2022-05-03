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
public class StringBucketResultNodeVector extends ResultNodeVector {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 114, StringBucketResultNodeVector.class);
    private ArrayList<StringBucketResultNode> vector = new ArrayList<StringBucketResultNode>();

    @Override
    protected int onGetClassId() {
        return classId;
    }

    public StringBucketResultNodeVector() {
    }

    public StringBucketResultNodeVector add(StringBucketResultNode v) {
        vector.add(v);
        return this;
    }

    public ResultNodeVector add(ResultNode r) {
        return add((StringBucketResultNode)r);
    }

    @Override public int size() { return vector.size(); }

    public ArrayList<StringBucketResultNode> getVector() {
        return vector;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putInt(null, vector.size());
        for (StringBucketResultNode node : vector) {
            node.serialize(buf);
        }
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        int sz = buf.getInt(null);
        vector = new ArrayList<StringBucketResultNode>();
        for (int i = 0; i < sz; i++) {
            StringBucketResultNode node = new StringBucketResultNode();
            node.deserialize(buf);
            vector.add(node);
        }
    }

    @Override
    protected int onCmp(ResultNode rhs) {
        if (classId != rhs.getClassId()) {
            return (classId - rhs.getClassId());
        }
        StringBucketResultNodeVector b = (StringBucketResultNodeVector)rhs;
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
