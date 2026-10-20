// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;
import java.util.ArrayList;
import java.util.List;

/**
 * Protocol representation of document-local grouping deduplication. The content node keeps each key tuple
 * once per document within each parent group. It must be the whole filter or the last condition of an
 * 'and'; elsewhere the content node rejects every row and reports an error.
 */
public class OncePerDocumentPredicateNode extends FilterExpressionNode {
    public static final int classId = registerClass(0x4000 + 186, OncePerDocumentPredicateNode.class, OncePerDocumentPredicateNode::new);
    private List<ExpressionNode> keys;

    public OncePerDocumentPredicateNode() {}
    public OncePerDocumentPredicateNode(List<ExpressionNode> keys) {
        if (keys.isEmpty()) throw new IllegalArgumentException("onceperdoc requires at least one key");
        this.keys = List.copyOf(keys);
    }
    public List<ExpressionNode> getKeys() { return keys; }
    @Override protected int onGetClassId() { return classId; }
    @Override public OncePerDocumentPredicateNode clone() {
        return new OncePerDocumentPredicateNode(keys.stream().map(ExpressionNode::clone).toList());
    }
    @Override protected void onSerialize(Serializer buf) {
        buf.putInt(null, keys.size());
        for (var key : keys) serializeOptional(buf, key);
    }
    @Override protected void onDeserialize(Deserializer buf) {
        var expressions = new ArrayList<ExpressionNode>();
        int count = buf.getInt(null);
        if (count <= 0) throw new IllegalArgumentException("onceperdoc requires at least one key");
        for (int i = 0; i < count; ++i) expressions.add((ExpressionNode) deserializeOptional(buf));
        keys = List.copyOf(expressions);
    }
    @Override public void visitMembers(ObjectVisitor visitor) { visitor.visit("keys", keys); }
    @Override public int hashCode() { return keys.hashCode(); }
    @Override public boolean equals(Object other) {
        return other instanceof OncePerDocumentPredicateNode that && keys.equals(that.keys);
    }
}
