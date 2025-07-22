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
public class OrPredicateNode extends MultiArgPredicateNode {

    public static final int classId = registerClass(0x4000 + 176, OrPredicateNode.class, OrPredicateNode::new);

    public OrPredicateNode() {}

    public OrPredicateNode(List<FilterExpressionNode> args) {
        super(args);
    }

    @Override
    public FilterExpressionNode clone() {
        return new OrPredicateNode(getArgs().map(list -> list.stream()
                .map(FilterExpressionNode::clone).toList()).orElse(null));
    }
    
    @Override
    protected int onGetClassId() {
        return classId;
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
