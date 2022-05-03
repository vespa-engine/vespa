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
public class IntegerResultNodeVector extends ResultNodeVector {

    public static final int classId = registerClass(0x4000 + 119, IntegerResultNodeVector.class);
    private ArrayList<IntegerResultNode> vector = new ArrayList<IntegerResultNode>();

    public IntegerResultNodeVector() {

    }

    public IntegerResultNodeVector add(IntegerResultNode v) {
        vector.add(v);
        return this;
    }

    public ArrayList<IntegerResultNode> getVector() {
        return vector;
    }

    @Override
    public ResultNodeVector add(ResultNode r) {
        return add((IntegerResultNode)r);
    }

    @Override public int size() { return vector.size(); }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        buf.putInt(null, vector.size());
        for (IntegerResultNode node : vector) {
            node.serialize(buf);
        }
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        int sz = buf.getInt(null);
        vector = new ArrayList<IntegerResultNode>();
        for (int i = 0; i < sz; i++) {
            IntegerResultNode node = new IntegerResultNode(0);
            node.deserialize(buf);
            vector.add(node);
        }
    }

    @Override
    protected int onCmp(ResultNode rhs) {
        if (classId != rhs.getClassId()) {
            return (classId - rhs.getClassId());
        }
        IntegerResultNodeVector b = (IntegerResultNodeVector)rhs;
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
