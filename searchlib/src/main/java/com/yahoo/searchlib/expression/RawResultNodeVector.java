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
public class RawResultNodeVector extends ResultNodeVector {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 115, RawResultNodeVector.class);
    private ArrayList<RawResultNode> vector = new ArrayList<RawResultNode>();

    @Override
    protected int onGetClassId() {
        return classId;
    }

    public RawResultNodeVector() {
    }

    public RawResultNodeVector add(RawResultNode v) {
        vector.add(v);
        return this;
    }

    public ResultNodeVector add(ResultNode r) {
        return add((RawResultNode)r);
    }

    @Override public int size() { return vector.size(); }

    public ArrayList<RawResultNode> getVector() {
        return vector;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putInt(null, vector.size());
        for (RawResultNode node : vector) {
            node.serialize(buf);
        }
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        int sz = buf.getInt(null);
        vector = new ArrayList<RawResultNode>();
        for (int i = 0; i < sz; i++) {
            RawResultNode node = new RawResultNode();
            node.deserialize(buf);
            vector.add(node);
        }
    }

    @Override
    protected int onCmp(ResultNode rhs) {
        if (classId != rhs.getClassId()) {
            return (classId - rhs.getClassId());
        }
        RawResultNodeVector b = (RawResultNodeVector)rhs;
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
