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
public class Int16ResultNodeVector extends ResultNodeVector {

    public static final int classId = registerClass(0x4000 + 117, Int16ResultNodeVector.class);
    private ArrayList<Int16ResultNode> vector = new ArrayList<Int16ResultNode>();

    public Int16ResultNodeVector() {
    }

    public Int16ResultNodeVector add(Int16ResultNode v) {
        vector.add(v);
        return this;
    }

    public ArrayList<Int16ResultNode> getVector() {
        return vector;
    }

    @Override
    public ResultNodeVector add(ResultNode r) {
        return add((Int16ResultNode)r);
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
        for (Int16ResultNode node : vector) {
            node.serialize(buf);
        }
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        int sz = buf.getInt(null);
        vector = new ArrayList<Int16ResultNode>();
        for (int i = 0; i < sz; i++) {
            Int16ResultNode node = new Int16ResultNode((short)0);
            node.deserialize(buf);
            vector.add(node);
        }
    }

    @Override
    protected int onCmp(ResultNode rhs) {
        if (classId != rhs.getClassId()) {
            return (classId - rhs.getClassId());
        }
        Int16ResultNodeVector b = (Int16ResultNodeVector)rhs;
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
