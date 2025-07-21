// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.util.List;

/**
 * Protocol class for {@code OrPredicate}.
 *
 * @author johsol
 */
public class AndPredicateNode extends MultiArgPredicateNode {

    public static final int classId = registerClass(0x4000 + 177, AndPredicateNode.class, AndPredicateNode::new);

    public AndPredicateNode() {}

    public AndPredicateNode(List<FilterExpressionNode> args) {
        super(args);
    }

    @Override protected int onGetClassId() { return classId; }

    @Override
    public FilterExpressionNode clone() {
        return new AndPredicateNode(getArgs().map(list -> list.stream()
                .map(FilterExpressionNode::clone).toList()).orElse(null));
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
