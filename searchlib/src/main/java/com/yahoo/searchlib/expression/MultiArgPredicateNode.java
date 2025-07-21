// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Protocol class for {@code OrPredicate}.
 *
 * @author johsol
 */
public class MultiArgPredicateNode extends FilterExpressionNode {

    public static final int classId = registerClass(0x4000 + 175, MultiArgPredicateNode.class, MultiArgPredicateNode::new);
    private List<FilterExpressionNode> args = new ArrayList<FilterExpressionNode>();

    public MultiArgPredicateNode() {}

    public MultiArgPredicateNode(List<FilterExpressionNode> args) {
        this.args = args;
    }

    public Optional<List<FilterExpressionNode>> getArgs() { return Optional.ofNullable(args); }

    @Override protected int onGetClassId() { return classId; }

    @Override
    public OrPredicateNode clone() {
        return new OrPredicateNode(args != null ? args.stream().map(FilterExpressionNode::clone).toList() : null);
    }

    @Override
    protected void onSerialize(Serializer buf) {
        int numArgs = args.size();
        buf.putInt(null, numArgs);
        for (FilterExpressionNode node : args) {
            serializeOptional(buf, node);
        }
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        args.clear();
        int numArgs = buf.getInt(null);
        for (int i = 0; i < numArgs; i++) {
            FilterExpressionNode node = (FilterExpressionNode)deserializeOptional(buf);
            args.add(node);
        }
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("args", args);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MultiArgPredicateNode rhs = (MultiArgPredicateNode)o;
        return args.equals(rhs.args);
    }

    @Override
    public int hashCode() {
        int ret = 0;
        for (FilterExpressionNode node : args) {
            ret += node.hashCode();
        }
        return ret;
    }
}
